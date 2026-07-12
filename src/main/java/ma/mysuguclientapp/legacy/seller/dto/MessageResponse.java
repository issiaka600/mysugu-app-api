package ma.mysuguclientapp.legacy.seller.dto;

/**
 * Réponse générique {"message": "..."} — reproduit le format 6valley que l'app vendeur
 * (Tiktak-vendor-app-moso) parse pour les mutations simples (OTP, updates, etc.).
 */
public record MessageResponse(String message) {
}
