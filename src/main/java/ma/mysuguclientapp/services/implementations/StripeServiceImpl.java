package ma.mysuguclientapp.services.implementations;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.config.StripeConfig;
import ma.mysuguclientapp.services.interfaces.StripeService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeServiceImpl implements StripeService {

    private final StripeConfig stripeConfig;

    @Override
    public String createPaymentIntent(BigDecimal montant, Long commandeId, String numeroCommande) {
        if (!stripeConfig.isEnabled()) {
            log.debug("Stripe désactivé — PaymentIntent non créé pour la commande {}", numeroCommande);
            return null;
        }

        try {
            // Stripe exige le montant en centimes (unité la plus petite)
            long montantCentimes = montant
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(montantCentimes)
                    .setCurrency(stripeConfig.getCurrency())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .putMetadata("commandeId", String.valueOf(commandeId))
                    .putMetadata("numeroCommande", numeroCommande)
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            log.info("PaymentIntent Stripe créé: {} pour la commande {} (montant: {} {})",
                    intent.getId(), numeroCommande, montant, stripeConfig.getCurrency().toUpperCase());
            return intent.getClientSecret();

        } catch (StripeException e) {
            log.error("Erreur Stripe lors de la création du PaymentIntent pour la commande {}: {}",
                    numeroCommande, e.getMessage());
            return null;
        }
    }

    @Override
    public void refundPaymentIntent(String paymentIntentId) {
        if (!stripeConfig.isEnabled()) {
            log.debug("Stripe désactivé — remboursement non effectué pour le PaymentIntent {}", paymentIntentId);
            return;
        }
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            log.warn("Tentative de remboursement sans paymentIntentId — ignoré");
            return;
        }

        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .build();

            Refund refund = Refund.create(params);
            log.info("Remboursement Stripe effectué: {} pour le PaymentIntent {}",
                    refund.getId(), paymentIntentId);

        } catch (StripeException e) {
            log.error("Erreur Stripe lors du remboursement du PaymentIntent {}: {}",
                    paymentIntentId, e.getMessage());
        }
    }
}
