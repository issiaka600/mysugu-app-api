package ma.mysuguclientapp.services.interfaces;

import java.math.BigDecimal;

public interface StripeService {

    /**
     * Crée un PaymentIntent Stripe pour un paiement par carte.
     *
     * @param montant         montant à débiter (en MAD ou devise configurée)
     * @param commandeId      identifiant de la commande (métadonnée)
     * @param numeroCommande  numéro de commande lisible (métadonnée)
     * @return le clientSecret du PaymentIntent (à transmettre au frontend), ou null si Stripe désactivé
     */
    String createPaymentIntent(BigDecimal montant, Long commandeId, String numeroCommande);

    /**
     * Extrait le paymentIntentId depuis un clientSecret.
     * Format Stripe : "pi_xxxx_secret_yyyy" → "pi_xxxx"
     */
    default String extractPaymentIntentId(String clientSecret) {
        if (clientSecret == null) return null;
        int idx = clientSecret.indexOf("_secret_");
        return idx > 0 ? clientSecret.substring(0, idx) : clientSecret;
    }

    /**
     * Rembourse intégralement un PaymentIntent Stripe.
     *
     * @param paymentIntentId identifiant du PaymentIntent à rembourser
     */
    void refundPaymentIntent(String paymentIntentId);
}
