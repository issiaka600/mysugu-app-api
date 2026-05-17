package ma.mysuguclientapp.services.implementations;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.AppleIdTokenClaimsDTO;
import ma.mysuguclientapp.exceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class AppleAuthService {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";

    @Value("${apple.auth.client-id:ma.mysuku.customer}")
    private String appleClientId;

    private volatile ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public AppleIdTokenClaimsDTO verifyIdToken(String idToken, String rawNonce) {
        if (idToken == null || idToken.isBlank()) {
            throw new BadRequestException("Le token Apple est requis");
        }
        if (rawNonce == null || rawNonce.isBlank()) {
            throw new BadRequestException("Le nonce Apple est requis");
        }
        if (appleClientId == null || appleClientId.isBlank()) {
            throw new BadRequestException("La configuration Apple Auth est incomplète: apple.auth.client-id manquant");
        }

        try {
            JWTClaimsSet claims = getJwtProcessor().process(idToken, null);
            validateStandardClaims(claims);
            validateNonce(claims.getStringClaim("nonce"), rawNonce);

            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new BadRequestException("Le token Apple ne contient pas d'identifiant utilisateur (sub)");
            }

            String email = claims.getStringClaim("email");
            boolean emailVerified = Boolean.TRUE.equals(claims.getBooleanClaim("email_verified"));

            return AppleIdTokenClaimsDTO.builder()
                    .sub(sub)
                    .email(email)
                    .emailVerified(emailVerified)
                    .build();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Echec validation token Apple: {}", e.getMessage());
            throw new BadRequestException("Le token Apple est invalide");
        }
    }

    private ConfigurableJWTProcessor<SecurityContext> getJwtProcessor() throws MalformedURLException {
        ConfigurableJWTProcessor<SecurityContext> processor = jwtProcessor;
        if (processor == null) {
            synchronized (this) {
                processor = jwtProcessor;
                if (processor == null) {
                    processor = new DefaultJWTProcessor<>();
                    JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(new URL(APPLE_JWKS_URL));
                    JWSKeySelector<SecurityContext> keySelector =
                            new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
                    processor.setJWSKeySelector(keySelector);
                    jwtProcessor = processor;
                }
            }
        }
        return processor;
    }

    private void validateStandardClaims(JWTClaimsSet claims) throws BadRequestException {
        if (!APPLE_ISSUER.equals(claims.getIssuer())) {
            throw new BadRequestException("Le token Apple a un émetteur invalide");
        }

        List<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(appleClientId)) {
            throw new BadRequestException("Le token Apple ne correspond pas au client configuré");
        }

        Date expiration = claims.getExpirationTime();
        if (expiration == null || expiration.before(new Date())) {
            throw new BadRequestException("Le token Apple est expiré");
        }
    }

    private void validateNonce(String tokenNonce, String rawNonce) {
        if (tokenNonce == null || tokenNonce.isBlank()) {
            throw new BadRequestException("Le token Apple ne contient pas de nonce");
        }

        String expectedHex = sha256Hex(rawNonce);
        if (expectedHex.equalsIgnoreCase(tokenNonce)) {
            return;
        }

        String expectedBase64Url = sha256Base64Url(rawNonce);
        if (expectedBase64Url.equals(tokenNonce)) {
            return;
        }

        throw new BadRequestException("Le nonce Apple ne correspond pas");
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static String sha256Base64Url(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
