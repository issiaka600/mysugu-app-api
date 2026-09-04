package ma.mysuguclientapp.legacy.deliveryman.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.deliveryman.mapper.LegacyOrderMapper;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.DispatchLivraisonService;
import ma.mysuguclientapp.services.interfaces.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Acceptation d'une offre de livraison réservée au livreur (POST /api/v2/delivery-man/{orderId}/accept).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryManOrderService {

    /** Statuts considérés comme "commande active" pour la règle une-commande-à-la-fois. */
    public static final List<StatutCommande> ACTIFS = List.of(
            StatutCommande.CONFIRMEE, StatutCommande.EN_PREPARATION,
            StatutCommande.PRETE, StatutCommande.ASSIGNEE_LIVREUR, StatutCommande.EN_COURS);

    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final LegacyOrderMapper mapper;
    private final DispatchLivraisonService dispatchLivraisonService;

    @Transactional
    public Map<String, Object> accept(Long orderId, User livreur) {
        Commande c = dispatchLivraisonService.accepterOffre(orderId, livreur);
        notifierApresAcceptation(c, livreur);
        return mapper.toOrderMap(c, false);
    }

    @Transactional
    public void reject(Long orderId, User livreur) {
        dispatchLivraisonService.refuserOffre(orderId, livreur);
    }

    private void notifierApresAcceptation(Commande c, User livreur) {
        String numero = c.getNumeroCommande();
        try {
            // La commande reste EN_PREPARATION jusqu'à ce que le vendeur la marque PRETE.
            // La notification représente cependant l'événement d'affectation, afin que le client
            // et le vendeur rafraîchissent immédiatement les informations du livreur.
            if (c.getClient() != null) {
                notificationService.envoyerNotificationStatutCommande(
                        c.getClient().getId(), numero, c.getId(), StatutCommande.ASSIGNEE_LIVREUR);
            }
            if (c.getRestaurant() != null && c.getRestaurant().getOwner() != null) {
                notificationService.envoyerNotificationStatutCommande(
                        c.getRestaurant().getOwner().getId(), numero, c.getId(), StatutCommande.ASSIGNEE_LIVREUR);
            }
            notificationService.envoyerNotificationStatutCommande(
                    livreur.getId(), numero, c.getId(), StatutCommande.ASSIGNEE_LIVREUR);
        } catch (Exception e) {
            log.warn("Notifications post-acceptation commande {} : {}", numero, e.getMessage());
        }
    }
}
