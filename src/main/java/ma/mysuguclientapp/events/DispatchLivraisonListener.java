package ma.mysuguclientapp.events;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.services.implementations.DispatchLivraisonService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DispatchLivraisonListener {
    private final DispatchLivraisonService dispatchLivraisonService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDispatch(DispatchLivraisonEvent event) {
        dispatchLivraisonService.proposerProchainLivreur(event.commandeId());
    }
}
