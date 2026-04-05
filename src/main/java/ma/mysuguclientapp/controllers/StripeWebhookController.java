package ma.mysuguclientapp.controllers;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.config.StripeConfig;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.repositories.CommandeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeConfig stripeConfig;
    private final CommandeRepository commandeRepository;

    /**
     * Point d'entrée des webhooks Stripe.
     * Stripe envoie les événements ici après chaque action de paiement.
     * Endpoint public (pas de JWT) — sécurisé par la vérification de signature Stripe.
     */
    @PostMapping(value = "/webhook", consumes = "application/json")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        String webhookSecret = stripeConfig.getWebhookSecret();

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("STRIPE_WEBHOOK_SECRET non configuré — webhook refusé");
            return ResponseEntity.badRequest().body("Webhook secret non configuré");
        }

        // Vérifie la signature Stripe pour s'assurer que la requête vient bien de Stripe
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Signature webhook Stripe invalide: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Signature invalide");
        }

        log.info("Événement Stripe reçu: {} ({})", event.getType(), event.getId());

        Optional<StripeObject> stripeObject = event.getDataObjectDeserializer().getObject();
        if (stripeObject.isEmpty()) {
            log.warn("Impossible de désérialiser l'objet Stripe pour l'événement {}", event.getId());
            return ResponseEntity.ok("Ignored");
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                PaymentIntent intent = (PaymentIntent) stripeObject.get();
                handlePaymentSucceeded(intent);
            }
            case "payment_intent.payment_failed" -> {
                PaymentIntent intent = (PaymentIntent) stripeObject.get();
                handlePaymentFailed(intent);
            }
            default -> log.debug("Événement Stripe ignoré: {}", event.getType());
        }

        return ResponseEntity.ok("OK");
    }

    private void handlePaymentSucceeded(PaymentIntent intent) {
        commandeRepository.findByStripePaymentIntentId(intent.getId()).ifPresentOrElse(
                commande -> {
                    commande.setStatutPaiement(StatutPaiement.PAYE);
                    commandeRepository.save(commande);
                    log.info("Paiement confirmé via Stripe pour la commande {} (PaymentIntent: {})",
                            commande.getNumeroCommande(), intent.getId());
                },
                () -> log.warn("Aucune commande trouvée pour le PaymentIntent: {}", intent.getId())
        );
    }

    private void handlePaymentFailed(PaymentIntent intent) {
        commandeRepository.findByStripePaymentIntentId(intent.getId()).ifPresentOrElse(
                commande -> {
                    commande.setStatutPaiement(StatutPaiement.ECHOUE);
                    commandeRepository.save(commande);
                    log.info("Paiement échoué via Stripe pour la commande {} (PaymentIntent: {})",
                            commande.getNumeroCommande(), intent.getId());
                },
                () -> log.warn("Aucune commande trouvée pour le PaymentIntent échoué: {}", intent.getId())
        );
    }
}
