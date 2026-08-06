package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.OffreLivraison;
import ma.mysuguclientapp.entities.TentativeOffreLivraison;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.entities.ZoneDeploiement;
import ma.mysuguclientapp.enumerations.ModeReceptionCommande;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutOffreLivraison;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.events.DispatchLivraisonEvent;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.OffreLivraisonRepository;
import ma.mysuguclientapp.repositories.TentativeOffreLivraisonRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.FcmService;
import ma.mysuguclientapp.util.Constants;
import ma.mysuguclientapp.util.DistanceCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Attribution séquentielle : une seule offre temporaire par commande et par livreur à la fois. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DispatchLivraisonService {

    private static final String CHANNEL_ID = "mysuku_delivery_orders_v2";
    private static final List<StatutCommande> COMMANDES_ACTIVES = List.of(
            StatutCommande.CONFIRMEE, StatutCommande.EN_PREPARATION, StatutCommande.PRETE,
            StatutCommande.ASSIGNEE_LIVREUR, StatutCommande.EN_COURS);

    private final CommandeRepository commandeRepository;
    private final OffreLivraisonRepository offreRepository;
    private final TentativeOffreLivraisonRepository tentativeOffreLivraisonRepository;
    private final UserRepository userRepository;
    private final FcmService fcmService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${delivery.offer.duration-seconds:30}")
    private long offerDurationSeconds;

    @Value("${delivery.offer.alert-interval-seconds:20}")
    private long offerAlertIntervalSeconds;

    @Value("${delivery.driver-location.max-age-seconds:120}")
    private long maxLocationAgeSeconds;

    /** Propose la commande au prochain livreur éligible, ordonné par distance au restaurant. */
    public void proposerProchainLivreur(Long commandeId) {
        Commande commande = commandeRepository.findByIdForUpdate(commandeId).orElse(null);
        if (!eligiblePourDispatch(commande) || offreRepository
                .findByCommandeIdAndStatut(commandeId, StatutOffreLivraison.PROPOSEE).isPresent()) {
            return;
        }

        if (commande.getRestaurant().getLocalisation() == null) {
            log.warn("Commande {} : restaurant sans position GPS, aucun livreur ne peut être proposé", commandeId);
            return;
        }

        Set<Long> dejaProposes = offreRepository.findByCommandeIdOrderBySequenceNumberAsc(commandeId).stream()
                .map(o -> o.getLivreur().getId())
                .collect(java.util.stream.Collectors.toSet());
        LocalDateTime recentAfter = LocalDateTime.now().minusSeconds(maxLocationAgeSeconds);

        record Candidat(User livreur, double distanceKm) { }
        Candidat candidat = userRepository.findByRoleAndIsActiveAndLivreurDisponible(UserRole.LIVREUR, true, true)
                .stream()
                .filter(l -> !dejaProposes.contains(l.getId()))
                .filter(l -> l.getLocalisation() != null
                        && l.getLocalisation().getLatitude() != null
                        && l.getLocalisation().getLongitude() != null
                        && l.getLastLocationAt() != null
                        && !l.getLastLocationAt().isBefore(recentAfter))
                .filter(l -> !offreRepository.existsByLivreurIdAndStatut(l.getId(), StatutOffreLivraison.PROPOSEE))
                .filter(l -> commandeRepository.countByLivreurIdAndStatutIn(l.getId(), COMMANDES_ACTIVES) == 0)
                .map(l -> new Candidat(l, DistanceCalculator.calculate(
                        commande.getRestaurant().getLocalisation().getLatitude(),
                        commande.getRestaurant().getLocalisation().getLongitude(),
                        l.getLocalisation().getLatitude(), l.getLocalisation().getLongitude())))
                .filter(c -> isLivreurDansZoneDuRestaurant(commande, c.livreur()))
                .min(Comparator.comparingDouble(Candidat::distanceKm))
                .orElse(null);

        if (candidat == null) {
            log.warn("Commande {} : aucun livreur éligible pour une offre séquentielle", commandeId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        OffreLivraison offre = offreRepository.save(OffreLivraison.builder()
                .commande(commande)
                .livreur(candidat.livreur())
                .statut(StatutOffreLivraison.PROPOSEE)
                .sequenceNumber((int) offreRepository.countByCommandeId(commandeId) + 1)
                .distanceKm(candidat.distanceKm())
                .expiresAt(now.plusSeconds(offerDurationSeconds))
                .nextAlertAt(now)
                .alertAttemptCount(0)
                .build());
        envoyerAlerteOffre(offre, now);
        log.info("Commande {} proposée au livreur {} ({} km, zone: {})", commandeId,
                candidat.livreur().getId(), Math.round(candidat.distanceKm() * 100.0) / 100.0,
                descriptionZoneDispatch(commande));
    }

    /** Accepte uniquement l'offre en cours du livreur : il ne peut plus prendre une commande libre. */
    public Commande accepterOffre(Long commandeId, User livreur) {
        Commande commande = commandeRepository.findByIdForUpdate(commandeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande introuvable."));
        OffreLivraison offre = offreRepository.findForUpdate(commandeId, livreur.getId(), StatutOffreLivraison.PROPOSEE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cette livraison ne vous est pas proposée ou a déjà expiré."));

        if (!eligiblePourDispatch(commande) || !LocalDateTime.now().isBefore(offre.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette offre n'est plus disponible.");
        }
        if (commandeRepository.countByLivreurIdAndStatutIn(livreur.getId(), COMMANDES_ACTIVES) > 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous avez déjà une commande active.");
        }

        offre.setStatut(StatutOffreLivraison.ACCEPTEE);
        offre.setRespondedAt(LocalDateTime.now());
        commande.setLivreur(livreur);
        livreur.setLivreurDisponible(false);
        userRepository.save(livreur);
        offreRepository.save(offre);
        return commandeRepository.save(commande);
    }

    /** Refus explicite : la prochaine offre est créée une fois la transaction validée. */
    public void refuserOffre(Long commandeId, User livreur) {
        OffreLivraison offre = offreRepository.findForUpdate(commandeId, livreur.getId(), StatutOffreLivraison.PROPOSEE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Aucune offre active pour cette commande."));
        offre.setStatut(StatutOffreLivraison.REFUSEE);
        offre.setRespondedAt(LocalDateTime.now());
        offreRepository.save(offre);
        eventPublisher.publishEvent(new DispatchLivraisonEvent(commandeId));
    }

    /** Expire les offres non répondues puis déclenche l'offre suivante. */
    @Scheduled(fixedDelayString = "${delivery.offer.poll-delay-ms:5000}")
    public void expirerOffres() {
        offreRepository.findExpiredIds(StatutOffreLivraison.PROPOSEE, LocalDateTime.now())
                .forEach(this::expirerOffre);
    }

    /** Relance l'alerte tant que l'offre est encore réservée au même livreur. */
    @Scheduled(fixedDelayString = "${delivery.offer.poll-delay-ms:5000}")
    public void rappelerOffres() {
        offreRepository.findAlertDueIds(StatutOffreLivraison.PROPOSEE, LocalDateTime.now())
                .forEach(this::rappelerOffre);
    }

    private void expirerOffre(Long offreId) {
        OffreLivraison offre = offreRepository.findByIdForUpdate(offreId).orElse(null);
        if (offre == null || offre.getStatut() != StatutOffreLivraison.PROPOSEE
                || LocalDateTime.now().isBefore(offre.getExpiresAt())) {
            return;
        }
        offre.setStatut(StatutOffreLivraison.EXPIREE);
        offre.setRespondedAt(LocalDateTime.now());
        offreRepository.save(offre);
        eventPublisher.publishEvent(new DispatchLivraisonEvent(offre.getCommande().getId()));
    }

    private void rappelerOffre(Long offreId) {
        OffreLivraison offre = offreRepository.findByIdForUpdate(offreId).orElse(null);
        if (offre == null || offre.getStatut() != StatutOffreLivraison.PROPOSEE
                || !LocalDateTime.now().isBefore(offre.getExpiresAt())) {
            return;
        }
        envoyerAlerteOffre(offre, LocalDateTime.now());
    }

    private void envoyerAlerteOffre(OffreLivraison offre, LocalDateTime now) {
        long ttlSeconds = Math.max(1L, Duration.between(now, offre.getExpiresAt()).toSeconds());
        ma.mysuguclientapp.services.FcmDeliveryResult result = fcmService.sendToUserWithResult(offre.getLivreur().getId(),
                "Nouvelle livraison",
                "Une commande est disponible",
                Map.ofEntries(
                        Map.entry("type", "order"),
                        Map.entry("event", "new_delivery"),
                        Map.entry("order_id", offre.getCommande().getId().toString()),
                        Map.entry("delivery_offer_id", offre.getId().toString()),
                        Map.entry("channelId", CHANNEL_ID),
                        Map.entry("androidSound", "order_alert"),
                        Map.entry("androidVisibility", "public"),
                        Map.entry("notificationTag", "order-" + offre.getCommande().getId()),
                        Map.entry("collapseKey", "order-" + offre.getCommande().getId()),
                        Map.entry("ttlSeconds", Long.toString(ttlSeconds)),
                        Map.entry("apnsSound", "order_alert.wav"),
                        Map.entry("apnsPushType", "alert"),
                        Map.entry("apnsPriority", "10"),
                        Map.entry("apnsInterruptionLevel", "time-sensitive"),
                        Map.entry("apnsThreadId", "order-" + offre.getCommande().getId()),
                        Map.entry("priority", "high")
                ));
        tentativeOffreLivraisonRepository.save(TentativeOffreLivraison.builder()
                .offre(offre)
                .tokensAttempted(result.tokensAttempted())
                .tokensSent(result.tokensSent())
                .firebaseMessageIds(joinAndTruncate(result.firebaseMessageIds()))
                .errorSummary(joinAndTruncate(result.errors()))
                .build());
        offre.setAlertAttemptCount(offre.getAlertAttemptCount() + 1);
        offre.setLastAlertAt(now);
        offre.setNextAlertAt(now.plusSeconds(offerAlertIntervalSeconds));
        offreRepository.save(offre);
    }

    private boolean eligiblePourDispatch(Commande commande) {
        return commande != null
                && commande.getLivreur() == null
                && commande.getModeReception() == ModeReceptionCommande.LIVRAISON
                && COMMANDES_ACTIVES.contains(commande.getStatut());
    }

    /**
     * Une zone de restaurant active et complète remplace le rayon global : un livreur doit se
     * trouver à l'intérieur du cercle configuré par l'admin. Les restaurants sans zone exploitable
     * gardent le repli historique de 15 km, pour ne pas interrompre les zones non encore paramétrées.
     */
    private boolean isLivreurDansZoneDuRestaurant(Commande commande, User livreur) {
        ZoneDeploiement zone = commande.getRestaurant().getZoneDeploiement();
        if (zone != null && Boolean.TRUE.equals(zone.getIsActive())
                && zone.getCentreLatitude() != null && zone.getCentreLongitude() != null
                && zone.getRayonKm() != null && zone.getRayonKm().signum() > 0) {
            return DistanceCalculator.isWithinRadius(
                    zone.getCentreLatitude(), zone.getCentreLongitude(),
                    livreur.getLocalisation().getLatitude(), livreur.getLocalisation().getLongitude(),
                    zone.getRayonKm().doubleValue());
        }

        return DistanceCalculator.isWithinRadius(
                commande.getRestaurant().getLocalisation().getLatitude(),
                commande.getRestaurant().getLocalisation().getLongitude(),
                livreur.getLocalisation().getLatitude(), livreur.getLocalisation().getLongitude(),
                Constants.AUTO_ASSIGN_RADIUS_KM);
    }

    private String descriptionZoneDispatch(Commande commande) {
        ZoneDeploiement zone = commande.getRestaurant().getZoneDeploiement();
        if (zone != null && Boolean.TRUE.equals(zone.getIsActive())
                && zone.getCentreLatitude() != null && zone.getCentreLongitude() != null
                && zone.getRayonKm() != null && zone.getRayonKm().signum() > 0) {
            return zone.getNom() + " (" + zone.getRayonKm() + " km)";
        }
        return "repli " + Constants.AUTO_ASSIGN_RADIUS_KM + " km";
    }

    private String joinAndTruncate(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        String value = String.join(" | ", values);
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
