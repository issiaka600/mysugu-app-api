package ma.mysuguclientapp.legacy.seller.dto;

/** Session vendeur. {@code token} reste l'alias historique de {@code accessToken}. */
public record TokenResponse(
        String token,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
