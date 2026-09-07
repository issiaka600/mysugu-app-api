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
import java.time.ZoneOffset;
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

    @Value("${delivery.seller-location.max-age-seconds:300}")
    private long maxSellerLocationAgeSeconds;

    /**
     * Propose la commande au prochain livreur éligible, ordonné par distance à la position GPS
     * récente du vendeur. Aucune adresse saisie manuellement ni ancienne position ne sert à
     * choisir un livreur.
     */
    public void proposerProchainLivreur(Long commandeId) {
        Commande commande = commandeRepository.findByIdForUpdate(commandeId).orElse(null);
        if (!eligiblePourDispatch(commande) || offreRepository
                .findByCommandeIdAndStatut(commandeId, StatutOffreLivraison.PROPOSEE).isPresent()) {
            return;
        }

        LocationPoint dispatchOrigin = resolveDispatchOrigin(commande, LocalDateTime.now());
        if (dispatchOrigin == null) {
            log.warn("Commande {} : position GPS récente du vendeur absente, aucun livreur ne peut être proposé",
                    commandeId);
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
                        dispatchOrigin.latitude(), dispatchOrigin.longitude(),
                        l.getLocalisation().getLatitude(), l.getLocalisation().getLongitude())))
                .filter(c -> isLivreurDansZoneDuRestaurant(commande, c.livreur(), dispatchOrigin))
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
        log.info("Commande {} proposée au livreur {} ({} km, origine: {}, zone: {})", commandeId,
                candidat.livreur().getId(), Math.round(candidat.distanceKm() * 100.0) / 100.0,
                dispatchOrigin.source(), descriptionZoneDispatch(commande));
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
        // Le livreur est réservé dès son acceptation, mais la préparation reste pilotée par le
        // vendeur. La commande ne pourra passer à EN_COURS qu'après son passage à PRETE.
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

    /**
     * Démarre le dispatch des commandes dont le délai post-EN_PREPARATION (posé par
     * CommandeServiceImpl#updateCommandeStatus, "sonneries persistantes" §2) est échu. Le champ
     * est remis à null avant publication pour ne pas re-déclencher au poll suivant — publier
     * l'événement suffit, {@code proposerProchainLivreur} gère lui-même l'idempotence (offre déjà
     * PROPOSEE, livreur déjà assigné, etc.).
     */
    @Scheduled(fixedDelayString = "${delivery.offer.poll-delay-ms:5000}")
    public void demarrerDispatchsDus() {
        commandeRepository.findDispatchLivreurDueIds(LocalDateTime.now()).forEach(commandeId -> {
            commandeRepository.findById(commandeId).ifPresent(c -> {
                c.setDispatchLivreurAt(null);
                commandeRepository.save(c);
            });
            eventPublisher.publishEvent(new DispatchLivraisonEvent(commandeId));
        });
    }

    /** Expire les offres non répondues puis déclenche l'offre suivante. */
    @Scheduled(fixedDelayString = "${delivery.offer.poll-delay-ms:5000}")
    public void expirerOffres() {
        offreRepository.findExpiredIds(StatutOffreLivraison.PROPOSEE, LocalDateTime.now())
                .forEach(this::expirerOffre);
    }

    /**
     * Ré-émission désactivée (correction PDF "Sonneries commandes", même traitement que
     * {@link AlerteCommandeVendeurService#sendDueAlerts()}) : renvoyer un FCM {@code notification}
     * complet toutes les {@code offerAlertIntervalSeconds} recréait une alerte "comme si c'était
     * une nouvelle commande" au lieu d'un son réellement persistant. L'unique envoi initial (voir
     * {@link #envoyerAlerteOffre}) porte désormais une notification Android FLAG_INSISTENT +
     * non-annulable côté app livreur : le son se répète en continu jusqu'à ce que le livreur
     * ouvre la commande. Ne concerne QUE le rappel au même livreur pendant sa fenêtre d'offre —
     * {@link #expirerOffres()} et la rotation vers le livreur suivant restent inchangés.
     */
    @Scheduled(fixedDelayString = "${delivery.offer.poll-delay-ms:5000}")
    public void rappelerOffres() {
        // Intentionnellement no-op — voir Javadoc ci-dessus.
    }

    /**
     * Filet de sécurité du dispatch. Un refus/une expiration déclenche normalement la prochaine
     * proposition après commit, mais aucune commande ne doit rester bloquée si aucun livreur
     * n'était disponible à cet instant (ou après un redémarrage de l'application).
     */
    @Scheduled(fixedDelayString = "${delivery.dispatch.retry-delay-ms:15000}")
    public void relancerCommandesSansOffre() {
        commandeRepository.findByStatutInAndLivreurIsNullOrderByCreatedAtAsc(
                        List.of(StatutCommande.EN_PREPARATION, StatutCommande.PRETE))
                .stream()
                .filter(c -> c.getModeReception() == ModeReceptionCommande.LIVRAISON)
                .map(Commande::getId)
                .forEach(this::proposerProchainLivreur);
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
                        Map.entry("expires_at", offre.getExpiresAt().toInstant(ZoneOffset.UTC).toString()),
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
                && (commande.getStatut() == StatutCommande.EN_PREPARATION
                    || commande.getStatut() == StatutCommande.PRETE);
    }

    /**
     * Une zone de restaurant active et complète remplace le rayon global : un livreur doit se
     * trouver à l'intérieur du cercle configuré par l'admin. Les restaurants sans zone exploitable
     * gardent le repli historique de 15 km, pour ne pas interrompre les zones non encore paramétrées.
     */
    private boolean isLivreurDansZoneDuRestaurant(Commande commande, User livreur,
                                                   LocationPoint dispatchOrigin) {
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
                dispatchOrigin.latitude(), dispatchOrigin.longitude(),
                livreur.getLocalisation().getLatitude(), livreur.getLocalisation().getLongitude(),
                Constants.AUTO_ASSIGN_RADIUS_KM);
    }

    private LocationPoint resolveDispatchOrigin(Commande commande, LocalDateTime now) {
        User seller = commande.getRestaurant().getOwner();
        if (hasCoordinates(seller)
                && seller.getLastLocationAt() != null
                && !seller.getLastLocationAt().isBefore(now.minusSeconds(maxSellerLocationAgeSeconds))) {
            return new LocationPoint(seller.getLocalisation().getLatitude(),
                    seller.getLocalisation().getLongitude(), "vendeur");
        }

        if (commande.getRestaurant() != null
                && commande.getRestaurant().getLocalisation() != null
                && commande.getRestaurant().getLocalisation().getLatitude() != null
                && commande.getRestaurant().getLocalisation().getLongitude() != null) {
            return new LocationPoint(commande.getRestaurant().getLocalisation().getLatitude(),
                    commande.getRestaurant().getLocalisation().getLongitude(), "restaurant");
        }

        return null;
    }

    private boolean hasCoordinates(User user) {
        return user != null && user.getLocalisation() != null
                && user.getLocalisation().getLatitude() != null
                && user.getLocalisation().getLongitude() != null;
    }

    private record LocationPoint(double latitude, double longitude, String source) { }

    private String descriptionZoneDispatch(Commande commande) {
        ZoneDeploiement zone = commande.getRestaurant().getZoneDeploiement();
        if (zone != null && Boolean.TRUE.equals(zone.getIsActive())
                && zone.getCentreLatitude() != null && zone.getCentreLongitude() != null
                && zone.getRayonKm() != null && zone.getRayonKm().signum() > 0) {
            return zone.getNom() + " (" + zone.getRayonKm() + " km)";
        }
        return "position GPS vendeur";
    }

    private String joinAndTruncate(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        String value = String.join(" | ", values);
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
