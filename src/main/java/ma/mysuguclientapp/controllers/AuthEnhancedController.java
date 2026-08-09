package ma.mysuguclientapp.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.auth.*;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.AuthEnhancedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthEnhancedController {

    private final AuthEnhancedService authEnhancedService;
    private final UserRepository userRepository;

    // ===== Email Verification =====

    @PostMapping("/api/auth/send-verification")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> envoyerVerification(@AuthenticationPrincipal String email) {
        Long userId = getUserId(email);
        authEnhancedService.envoyerEmailVerification(userId);
        return ResponseEntity.ok(Map.of("message", "Email de vérification envoyé"));
    }

    /** Permet de remplacer un lien expiré avant la première connexion. */
    @PostMapping("/api/auth/resend-verification")
    public ResponseEntity<Map<String, String>> renvoyerVerification(@RequestBody ForgotPasswordDTO dto) {
        authEnhancedService.renvoyerEmailVerification(dto.getEmail());
        return ResponseEntity.ok(Map.of(
                "message", "Si cette adresse nécessite une vérification, un nouvel email a été envoyé"
        ));
    }

    @PostMapping("/api/auth/verify-email")
    public ResponseEntity<Map<String, String>> verifierEmail(@RequestBody VerifyEmailDTO dto) {
        authEnhancedService.verifierEmail(dto);
        return ResponseEntity.ok(Map.of("message", "Email vérifié avec succès"));
    }

    // ===== Password Reset =====

    @PostMapping("/api/auth/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordDTO dto) {
        authEnhancedService.demanderReinitialisationMotDePasse(dto);
        return ResponseEntity.ok(Map.of("message", "Si votre email est enregistré, vous recevrez un lien de réinitialisation"));
    }

    @PostMapping("/api/auth/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordDTO dto) {
        authEnhancedService.reinitialiserMotDePasse(dto);
        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé avec succès"));
    }

    // ===== OTP (alternative mobile au lien email) =====

    @PostMapping("/api/auth/send-otp")
    public ResponseEntity<Map<String, String>> envoyerOtp(@RequestBody ForgotPasswordDTO dto) {
        authEnhancedService.demanderCodeOtp(dto);
        return ResponseEntity.ok(Map.of("message", "Si votre email est enregistré, vous recevrez un code de vérification"));
    }

    @PostMapping("/api/auth/verify-otp")
    public ResponseEntity<Map<String, String>> verifierOtp(@RequestBody VerifyOtpDTO dto) {
        String resetToken = authEnhancedService.verifierOtp(dto);
        return ResponseEntity.ok(Map.of(
                "message", "Code vérifié avec succès",
                "resetToken", resetToken
        ));
    }

    // ===== Token Management =====

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<RefreshTokenResponseDTO> rafraichirToken(@RequestBody RefreshTokenRequestDTO dto) {
        return ResponseEntity.ok(authEnhancedService.rafraichirToken(dto));
    }

    @PostMapping("/api/auth/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request,
                                                       @RequestBody(required = false) LogoutDTO dto) {
        String accessToken = extraireToken(request);
        authEnhancedService.logout(accessToken, dto != null ? dto : new LogoutDTO());
        return ResponseEntity.ok(Map.of("message", "Déconnexion réussie"));
    }

    @PostMapping("/api/auth/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> changerMotDePasse(@AuthenticationPrincipal String email,
                                                                   @RequestBody ChangePasswordDTO dto) {
        Long userId = getUserId(email);
        authEnhancedService.changerMotDePasse(userId, dto);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès"));
    }

    // ===== Address Book =====

    @GetMapping("/api/users/adresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AdresseLivraisonDTO>> getMesAdresses(@AuthenticationPrincipal String email) {
        Long userId = getUserId(email);
        return ResponseEntity.ok(authEnhancedService.getMesAdresses(userId));
    }

    @PostMapping("/api/users/adresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AdresseLivraisonDTO> ajouterAdresse(@AuthenticationPrincipal String email,
                                                               @RequestBody AdresseLivraisonCreateDTO dto) {
        Long userId = getUserId(email);
        return ResponseEntity.status(HttpStatus.CREATED).body(authEnhancedService.ajouterAdresse(userId, dto));
    }

    @PutMapping("/api/users/adresses/{adresseId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AdresseLivraisonDTO> modifierAdresse(@AuthenticationPrincipal String email,
                                                                @PathVariable Long adresseId,
                                                                @RequestBody AdresseLivraisonCreateDTO dto) {
        Long userId = getUserId(email);
        return ResponseEntity.ok(authEnhancedService.modifierAdresse(userId, adresseId, dto));
    }

    @DeleteMapping("/api/users/adresses/{adresseId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> supprimerAdresse(@AuthenticationPrincipal String email,
                                                  @PathVariable Long adresseId) {
        Long userId = getUserId(email);
        authEnhancedService.supprimerAdresse(userId, adresseId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/users/adresses/{adresseId}/default")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AdresseLivraisonDTO> setAdresseParDefaut(@AuthenticationPrincipal String email,
                                                                    @PathVariable Long adresseId) {
        Long userId = getUserId(email);
        return ResponseEntity.ok(authEnhancedService.definirAdresseParDefaut(userId, adresseId));
    }

    // ===== RGPD =====

    @DeleteMapping("/api/users/compte")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> supprimerMonCompte(@AuthenticationPrincipal String email) {
        Long userId = getUserId(email);
        authEnhancedService.supprimerCompte(userId);
        return ResponseEntity.ok(Map.of("message", "Votre compte a été supprimé conformément au RGPD"));
    }

    private Long getUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }

    private String extraireToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
