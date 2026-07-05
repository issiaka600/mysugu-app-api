package ma.mysuguclientapp.legacy.deliveryman.dto;

/** Réponse de login 6valley — {token}. L'app traite le token comme opaque (ici un JWT MySugu). */
public record TokenResponse(String token) {
}
