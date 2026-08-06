package ma.mysuguclientapp.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.services.implementations.AlerteCommandeVendeurService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Lance l'alerte uniquement après la validation de la transaction de création de commande. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommandeCreeeListener {

    private final AlerteCommandeVendeurService alerteCommandeVendeurService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommandeCreee(CommandeCreeeEvent event) {
        try {
            alerteCommandeVendeurService.sendImmediatelyForCommande(event.commandeId());
        } catch (Exception e) {
            // La campagne reste due ; le scheduler tentera à nouveau l'envoi.
            log.error("Échec de l'envoi immédiat de l'alerte vendeur pour commande {}", event.commandeId(), e);
        }
    }
}
