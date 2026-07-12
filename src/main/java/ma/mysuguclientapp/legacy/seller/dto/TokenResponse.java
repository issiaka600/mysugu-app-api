package ma.mysuguclientapp.legacy.seller.dto;

/** Réponse de login/registration 6valley — {token}. L'app traite le token comme opaque (JWT MySugu). */
public record TokenResponse(String token) {
}
