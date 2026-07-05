package ma.mysuguclientapp.legacy.deliveryman.dto;

/**
 * Réponse générique {"message": "..."} — reproduit le format 6valley que l'app
 * Tiktak (moso) parse pour les mutations simples (login errors, updates, etc.).
 *
 * Fait partie de la couche de compatibilité legacy (/api/v2/delivery-man/*) qui
 * adosse le contrat 6valley aux entités natives MySugu. Voir
 * docs/TIKTAK_LIVREUR_MIGRATION_TECHSPEC.md.
 */
public record MessageResponse(String message) {
}
