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
import java.util.List;

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

    /** Réveille les campagnes arrivées à échéance. La ligne est verrouillée pendant chaque envoi. */
    @Scheduled(fixedDelayString = "${order.seller-alert.poll-delay-ms:5000}")
    public void sendDueAlerts() {
        List<Long> dueIds = alerteRepository.findDueIds(StatutAlerteCommandeVendeur.ACTIVE, LocalDateTime.now());
        dueIds.forEach(id -> sender.sendDueAlert(id, false));
    }
}
