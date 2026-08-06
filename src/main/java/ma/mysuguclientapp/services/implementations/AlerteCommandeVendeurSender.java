package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.AlerteCommandeVendeur;
import ma.mysuguclientapp.entities.AlerteCommandeVendeurTentative;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.enumerations.StatutAlerteCommandeVendeur;
import ma.mysuguclientapp.repositories.AlerteCommandeVendeurRepository;
import ma.mysuguclientapp.repositories.AlerteCommandeVendeurTentativeRepository;
import ma.mysuguclientapp.services.FcmDeliveryResult;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Exécute un envoi dans sa propre transaction et verrouille la campagne afin d'éviter les doublons. */
@Service
@RequiredArgsConstructor
public class AlerteCommandeVendeurSender {

    private static final String CHANNEL_ID = "mysuku_seller_orders_v1";

    private final AlerteCommandeVendeurRepository alerteRepository;
    private final AlerteCommandeVendeurTentativeRepository tentativeRepository;
    private final FcmService fcmService;

    @Value("${order.seller-alert.interval-seconds:20}")
    private long intervalSeconds;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendDueAlert(Long alertId, boolean force) {
        AlerteCommandeVendeur alerte = alerteRepository.findByIdForUpdate(alertId).orElse(null);
        if (alerte == null || alerte.getStatut() != StatutAlerteCommandeVendeur.ACTIVE) return;

        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(alerte.getExpiresAt())) {
            alerte.setStatut(StatutAlerteCommandeVendeur.EXPIREE);
            alerteRepository.save(alerte);
            return;
        }
        if (!force && now.isBefore(alerte.getNextAttemptAt())) return;

        Commande commande = alerte.getCommande();
        FcmDeliveryResult result = fcmService.sendToUserWithResult(
                alerte.getVendeur().getId(),
                "Nouvelle commande",
                "Une nouvelle commande attend votre validation",
                Map.ofEntries(
                        Map.entry("type", "order"),
                        Map.entry("event", "new_order"),
                        Map.entry("order_id", commande.getId().toString()),
                        Map.entry("channelId", CHANNEL_ID),
                        Map.entry("androidSound", "order_alert"),
                        Map.entry("androidVisibility", "public"),
                        Map.entry("notificationTag", "order-" + commande.getId()),
                        Map.entry("collapseKey", "order-" + commande.getId()),
                        Map.entry("apnsSound", "order_alert.wav"),
                        Map.entry("apnsPushType", "alert"),
                        Map.entry("apnsPriority", "10"),
                        Map.entry("apnsInterruptionLevel", "time-sensitive"),
                        Map.entry("apnsThreadId", "order-" + commande.getId()),
                        Map.entry("priority", "high")
                ));

        tentativeRepository.save(AlerteCommandeVendeurTentative.builder()
                .alerte(alerte)
                .tokensAttempted(result.tokensAttempted())
                .tokensSent(result.tokensSent())
                .firebaseMessageIds(joinAndTruncate(result.firebaseMessageIds()))
                .errorSummary(joinAndTruncate(result.errors()))
                .build());

        alerte.setAttemptCount(alerte.getAttemptCount() + 1);
        alerte.setLastAttemptAt(now);
        alerte.setNextAttemptAt(now.plusSeconds(intervalSeconds));
        alerteRepository.save(alerte);
    }

    private String joinAndTruncate(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        String value = String.join(" | ", values);
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
