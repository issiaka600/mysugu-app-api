package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.entities.OtpResetSeller;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.seller.dto.ErrorsResponse;
import ma.mysuguclientapp.legacy.seller.dto.MessageResponse;
import ma.mysuguclientapp.legacy.seller.dto.TokenResponse;
import ma.mysuguclientapp.repositories.OtpResetSellerRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Auth du shim vendeur (contrat 6valley /api/v3/seller/auth/*).
 * Login par email + mot de passe -> JWT MySugu (traité comme token opaque par l'app),
 * en exigeant le rôle RESTAURANT_OWNER.
 * Voir docs/superpowers/specs/2026-07-10-vendor-3a-auth-design.md.
 */
@RestController
@RequestMapping("/api/v3/seller/auth")
@RequiredArgsConstructor
@Slf4j
public class SellerAuthController {

    private final UserRepository userRepository;
    private final OtpResetSellerRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    private static final SecureRandom RANDOM = new SecureRandom();

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        String email = str(body.get("email"));
        String password = str(body.get("password"));
        User u = email != null ? userRepository.findByEmail(email).orElse(null) : null;
        if (u == null || password == null || !passwordEncoder.matches(password, u.getPassword())
                || u.getRole() != UserRole.RESTAURANT_OWNER) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorsResponse.of("auth-001", "Identifiants incorrects."));
        }
        if (Boolean.FALSE.equals(u.getIsActive())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorsResponse.of("auth-002", "Compte désactivé."));
        }
        return ResponseEntity.ok(new TokenResponse(jwtTokenProvider.generateToken(u)));
    }

    /** forgot-password {identity} : envoie un OTP 4 chiffres (2 min) par email au vendeur. */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, Object> body) {
        String identity = str(body.get("identity"));
        if (identity == null || identity.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorsResponse.of("not-found", "Identifiant requis."));
        }
        User owner = findOwnerByIdentity(identity);
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorsResponse.of("not-found", "Aucun compte vendeur pour cet identifiant."));
        }
        String code = String.format("%04d", RANDOM.nextInt(10000));
        otpRepository.save(OtpResetSeller.builder()
                .identity(identity)
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(2))
                .build());
        if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
            try {
                emailService.envoyerNotificationRevueRestaurant(owner.getEmail(),
                        "MySugu - Code de réinitialisation",
                        "Bonjour,\n\nVotre code de réinitialisation est : " + code
                                + "\nCe code expire dans 2 minutes.\n\nL'équipe MySugu.");
            } catch (Exception e) {
                log.warn("Échec envoi email OTP vendeur à {}: {}", owner.getEmail(), e.getMessage());
            }
        }
        log.info("OTP reset vendeur généré pour {} (exp 2min)", identity);
        return ResponseEntity.ok(new MessageResponse("OTP envoyé avec succès."));
    }

    /** verify-otp {identity, otp}. */
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, Object> body) {
        String identity = str(body.get("identity"));
        String otp = str(body.get("otp"));
        if (identity == null || otp == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageResponse("Identifiant ou code manquant."));
        }
        OtpResetSeller row = otpRepository
                .findFirstByIdentityAndCodeAndUsedAtIsNullOrderByCreatedAtDesc(identity, otp)
                .orElse(null);
        if (row == null || row.isExpired()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageResponse("OTP invalide ou expiré."));
        }
        return ResponseEntity.ok(Map.of("message", "OTP vérifié avec succès.", "identity", identity));
    }

    /** reset-password (_method:put) {identity, otp, password, confirm_password}. */
    @PostMapping("/reset-password")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, Object> body) {
        String identity = str(body.get("identity"));
        String otp = str(body.get("otp"));
        String password = str(body.get("password"));
        String confirm = str(body.get("confirm_password"));
        if (password == null || password.length() < 8 || !password.equals(confirm)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorsResponse.of("password", "Mot de passe invalide ou non confirmé (min 8)."));
        }
        OtpResetSeller row = (identity != null && otp != null)
                ? otpRepository.findFirstByIdentityAndCodeAndUsedAtIsNullOrderByCreatedAtDesc(identity, otp).orElse(null)
                : null;
        if (row == null || row.isExpired()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorsResponse.of("otp", "OTP invalide ou expiré."));
        }
        User owner = findOwnerByIdentity(identity);
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorsResponse.of("not-found", "Compte introuvable."));
        }
        owner.setPassword(passwordEncoder.encode(password));
        userRepository.save(owner);
        otpRepository.deleteByIdentity(identity);
        return ResponseEntity.ok(new MessageResponse("Mot de passe réinitialisé avec succès."));
    }

    /** Résout un vendeur RESTAURANT_OWNER par identité (email, ou téléphone en repli). */
    private User findOwnerByIdentity(String identity) {
        User byEmail = userRepository.findByEmail(identity)
                .filter(u -> u.getRole() == UserRole.RESTAURANT_OWNER)
                .orElse(null);
        if (byEmail != null) {
            return byEmail;
        }
        return userRepository.findByTelephoneAndRole(identity, UserRole.RESTAURANT_OWNER)
                .stream().findFirst().orElse(null);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
