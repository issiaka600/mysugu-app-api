package ma.mysuguclientapp.legacy.deliveryman.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.entities.OtpResetLivreur;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.deliveryman.dto.ErrorsResponse;
import ma.mysuguclientapp.legacy.deliveryman.dto.LoginRequest;
import ma.mysuguclientapp.legacy.deliveryman.dto.MessageResponse;
import ma.mysuguclientapp.legacy.deliveryman.dto.TokenResponse;
import ma.mysuguclientapp.repositories.OtpResetLivreurRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Auth du shim livreur (contrat 6valley /api/v2/delivery-man/auth/*).
 * Login par indicatif+téléphone+mot de passe -> JWT MySugu (traité comme token opaque par l'app).
 * OTP de reset stocké dans OtpResetLivreur (4 chiffres, 2 min), envoyé par email.
 * Voir docs/legacy-contracts/CONTRACT-REFERENCE.md §1.
 */
@RestController
@RequestMapping("/api/v2/delivery-man/auth")
@RequiredArgsConstructor
@Slf4j
public class DeliveryManAuthController {

    private final UserRepository userRepository;
    private final OtpResetLivreurRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final ma.mysuguclientapp.services.interfaces.FcmService fcmService;

    private static final SecureRandom RANDOM = new SecureRandom();

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req.phone() == null || req.password() == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorsResponse.of("credential", "Téléphone et mot de passe requis."));
        }
        User livreur = findLivreurByPhone(req.phone());
        if (livreur == null || !passwordEncoder.matches(req.password(), livreur.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorsResponse.of("auth-001", "Identifiants incorrects."));
        }
        if (Boolean.FALSE.equals(livreur.getIsActive())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorsResponse.of("auth-002", "Votre compte est désactivé."));
        }
        String jwt = jwtTokenProvider.generateToken(livreur);
        return ResponseEntity.ok(new TokenResponse(jwt));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, Object> body) {
        String phone = str(body.get("phone"));
        String countryCode = str(body.get("country_code"));
        if (phone == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorsResponse.of("not-found", "Numéro de téléphone requis."));
        }
        User livreur = findLivreurByPhone(phone);
        if (livreur == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorsResponse.of("not-found", "Aucun compte livreur pour ce numéro."));
        }
        String code = String.format("%04d", RANDOM.nextInt(10000));
        otpRepository.save(OtpResetLivreur.builder()
                .telephone(phone)
                .countryCode(countryCode)
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(2))
                .build());
        // Canal email + push FCM (best effort). L'OTP est loggé pour l'observabilité/tests.
        if (livreur.getEmail() != null && !livreur.getEmail().isBlank()) {
            try {
                emailService.envoyerNotificationRevueRestaurant(livreur.getEmail(),
                        "MySugu - Code de réinitialisation",
                        "Bonjour,\n\nVotre code de réinitialisation est : " + code
                                + "\nCe code expire dans 2 minutes.\n\nL'équipe MySugu.");
            } catch (Exception e) {
                log.warn("Échec envoi email OTP à {}: {}", livreur.getEmail(), e.getMessage());
            }
        }
        try {
            fcmService.sendToUser(livreur.getId(), "Code de réinitialisation",
                    "Votre code de réinitialisation MySugu est : " + code + " (valable 2 min).",
                    java.util.Map.of("type", "otp_reset"));
        } catch (Exception e) {
            log.warn("Échec push FCM OTP livreur {}: {}", livreur.getId(), e.getMessage());
        }
        log.info("OTP reset livreur généré pour {} (exp 2min)", phone);
        return ResponseEntity.ok(new MessageResponse("OTP envoyé avec succès."));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, Object> body) {
        String otp = str(body.get("otp"));
        String phone = str(body.get("phone"));
        if (otp == null || phone == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageResponse("Code ou téléphone manquant."));
        }
        OtpResetLivreur row = otpRepository
                .findFirstByTelephoneAndCodeAndUsedAtIsNullOrderByCreatedAtDesc(phone, otp)
                .orElse(null);
        if (row == null || row.isExpired()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageResponse("OTP invalide ou expiré."));
        }
        return ResponseEntity.ok(Map.of("message", "OTP vérifié avec succès.", "phone", phone));
    }

    @PostMapping("/reset-password")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, Object> body) {
        String phone = str(body.get("phone"));
        String password = str(body.get("password"));
        String confirm = str(body.get("confirm_password"));
        if (password == null || password.length() < 8 || !password.equals(confirm)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorsResponse.of("password", "Mot de passe invalide ou non confirmé (min 8)."));
        }
        User livreur = phone != null ? findLivreurByPhone(phone) : null;
        if (livreur == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorsResponse.of("not-found", "Compte introuvable."));
        }
        livreur.setPassword(passwordEncoder.encode(password));
        userRepository.save(livreur);
        otpRepository.deleteByTelephone(phone);
        return ResponseEntity.ok(new MessageResponse("Mot de passe réinitialisé avec succès."));
    }

    /** Résout un livreur par téléphone (numéro tel que saisi par l'app). Premier match LIVREUR. */
    private User findLivreurByPhone(String phone) {
        List<User> matches = userRepository.findByTelephoneAndRole(phone, UserRole.LIVREUR);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
