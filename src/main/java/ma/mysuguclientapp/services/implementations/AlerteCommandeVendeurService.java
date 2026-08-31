package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.AlerteCommandeVendeur;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutAlerteCommandeVendeur;
import ma.mysuguclientapp.repositories.AlerteCommandeVendeurRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Gère l'alerte répétée d'une nouvelle commande pour son vendeur.
 * L'envoi est audité ; l'acceptation Firebase ne doit pas être interprétée comme une lecture mobile.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlerteCommandeVendeurService {

    private final AlerteCommandeVendeurRepository alerteRepository;
    private final AlerteCommandeVendeurSender sender;

    @Value("${order.seller-alert.duration-seconds:120}")
    private long durationSeconds;

    /** Crée la campagne sans effet externe : l'envoi initial intervient après le commit de la commande. */
    @Transactional
    public void createForCommande(Commande commande) {
        User vendeur = commande.getRestaurant() != null ? commande.getRestaurant().getOwner() : null;
        if (vendeur == null) {
            log.warn("Commande {} sans vendeur associé : alerte vendeur non créée", commande.getId());
            return;
        }
        if (alerteRepository.findByCommandeId(commande.getId()).isPresent()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        alerteRepository.save(AlerteCommandeVendeur.builder()
                .commande(commande)
                .vendeur(vendeur)
                .statut(StatutAlerteCommandeVendeur.ACTIVE)
                .nextAttemptAt(now)
                .expiresAt(now.plusSeconds(durationSeconds))
                .attemptCount(0)
                .build());
    }

    /** Déclenche l'envoi initial une fois que la commande est réellement sauvegardée. */
    public void sendImmediatelyForCommande(Long commandeId) {
        alerteRepository.findByCommandeId(commandeId)
                .ifPresent(alerte -> sender.sendDueAlert(alerte.getId(), true));
    }

    /** Arrête définitivement les répétitions dès que le vendeur a pris une décision. */
    @Transactional
    public void stopForCommande(Long commandeId, String reason) {
        alerteRepository.findByCommandeId(commandeId).ifPresent(alerte -> {
            if (alerte.getStatut() == StatutAlerteCommandeVendeur.ACTIVE) {
                alerte.setStatut(StatutAlerteCommandeVendeur.ARRETEE);
                alerte.setStoppedAt(LocalDateTime.now());
                alerte.setStopReason(reason);
                alerteRepository.save(alerte);
            }
        });
    }

    /**
     * Ré-émission désactivée (correction PDF "Sonneries commandes") : renvoyer un FCM
     * {@code notification} complet toutes les {@code intervalSeconds} recréait à chaque fois une
     * alerte visuellement identique à une nouvelle commande ("ça sonne une fois, puis mets un peu
     * de temps avant de sonner encore... avec le même message... comme si c'était une nouvelle
     * commande") et n'offrait qu'un anneau intermittent plutôt qu'un son réellement persistant.
     * Le seul envoi (voir {@link #sendImmediatelyForCommande}) porte désormais une notification
     * Android FLAG_INSISTENT + non-annulable (MyNotification.showBigTextNotification côté app
     * vendeur) : le son/la vibration se répètent en continu jusqu'à ce que le vendeur ouvre la
     * commande, sans ré-envoi serveur. L'infrastructure d'audit (AlerteCommandeVendeur,
     * stopForCommande) reste en place si une ré-émission de secours devait être réintroduite.
     */
    @Scheduled(fixedDelayString = "${order.seller-alert.poll-delay-ms:5000}")
    public void sendDueAlerts() {
        // Intentionnellement no-op — voir Javadoc ci-dessus.
    }
}
