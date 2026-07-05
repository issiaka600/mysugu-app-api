package ma.mysuguclientapp.legacy.deliveryman.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.TypeNotification;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.deliveryman.mapper.LegacyOrderMapper;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Revendication FCFS d'une commande par un livreur (POST /api/v2/delivery-man/{orderId}/accept).
 * Reproduit acceptOrderFCFS 6valley : verrou de ligne, une seule commande active par livreur,
 * diffusion "trop tard" aux autres livreurs. Voir techspec §8.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryManOrderService {

    /** Statuts considérés comme "commande active" pour la règle une-commande-à-la-fois. */
    public static final List<StatutCommande> ACTIFS = List.of(
            StatutCommande.CONFIRMEE, StatutCommande.EN_PREPARATION,
            StatutCommande.PRETE, StatutCommande.ASSIGNEE_LIVREUR, StatutCommande.EN_COURS);

    /** Statuts d'une commande non assignée encore revendiquable. */
    public static final List<StatutCommande> REVENDIQUABLES = List.of(
            StatutCommande.EN_ATTENTE, StatutCommande.CONFIRMEE,
            StatutCommande.EN_PREPARATION, StatutCommande.PRETE);

    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final LegacyOrderMapper mapper;

    @Transactional
    public Map<String, Object> accept(Long orderId, User livreur) {
        Commande c = commandeRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande introuvable."));

        // Déjà revendiquée par un autre livreur ?
        if (c.getLivreur() != null && !c.getLivreur().getId().equals(livreur.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette commande a déjà été prise par un autre livreur.");
        }

        if (c.getLivreur() == null) {
            // Règle : une seule commande active par livreur.
            if (commandeRepository.countByLivreurIdAndStatutIn(livreur.getId(), ACTIFS) > 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous avez déjà une commande active.");
            }
            if (!REVENDIQUABLES.contains(c.getStatut())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette commande n'est plus disponible.");
            }
            c.setLivreur(livreur);
            if (c.getStatut() == StatutCommande.EN_ATTENTE) {
                c.setStatut(StatutCommande.CONFIRMEE);
            }
            commandeRepository.save(c);
            livreur.setLivreurDisponible(false);
            userRepository.save(livreur);
            notifierApresAcceptation(c, livreur);
        }
        return mapper.toOrderMap(c, false);
    }

    private void notifierApresAcceptation(Commande c, User livreur) {
        String numero = c.getNumeroCommande();
        try {
            if (c.getClient() != null) {
                notificationService.envoyerNotification(c.getClient().getId(),
                        "Livreur en route",
                        "Votre commande " + numero + " a été prise en charge par un livreur.",
                        TypeNotification.LIVREUR_ASSIGNE, c.getId(), "COMMANDE");
            }
            if (c.getRestaurant() != null && c.getRestaurant().getOwner() != null) {
                notificationService.envoyerNotification(c.getRestaurant().getOwner().getId(),
                        "Livreur assigné",
                        "Un livreur a pris la commande " + numero + ".",
                        TypeNotification.LIVREUR_ASSIGNE, c.getId(), "COMMANDE");
            }
            // Broadcast "trop tard" aux autres livreurs disponibles.
            userRepository.findByRoleAndIsActiveAndLivreurDisponible(UserRole.LIVREUR, true, true)
                    .stream()
                    .filter(l -> !l.getId().equals(livreur.getId()))
                    .forEach(l -> notificationService.envoyerNotification(l.getId(),
                            "Commande déjà prise",
                            "La commande " + numero + " a été prise par un autre livreur.",
                            TypeNotification.COMMANDE_CONFIRMEE, c.getId(), "COMMANDE"));
        } catch (Exception e) {
            log.warn("Notifications post-acceptation commande {} : {}", numero, e.getMessage());
        }
    }
}
