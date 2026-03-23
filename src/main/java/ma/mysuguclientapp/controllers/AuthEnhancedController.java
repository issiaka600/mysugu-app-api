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
import org.springframework.security.core.userdetails.UserDetails;
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
    public ResponseEntity<Map<String, String>> envoyerVerification(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        authEnhancedService.envoyerEmailVerification(userId);
        return ResponseEntity.ok(Map.of("message", "Email de vérification envoyé"));
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
    public ResponseEntity<Map<String, String>> changerMotDePasse(@AuthenticationPrincipal UserDetails userDetails,
                                                                   @RequestBody ChangePasswordDTO dto) {
        Long userId = getUserId(userDetails);
        authEnhancedService.changerMotDePasse(userId, dto);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès"));
    }

    // ===== Address Book =====

    @GetMapping("/api/users/adresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AdresseLivraisonDTO>> getMesAdresses(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(authEnhancedService.getMesAdresses(userId));
    }

    @PostMapping("/api/users/adresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AdresseLivraisonDTO> ajouterAdresse(@AuthenticationPrincipal UserDetails userDetails,
                                                               @RequestBody AdresseLivraisonCreateDTO dto) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED).body(authEnhancedService.ajouterAdresse(userId, dto));
    }

    @PutMapping("/api/users/adresses/{adresseId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AdresseLivraisonDTO> modifierAdresse(@AuthenticationPrincipal UserDetails userDetails,
                                                                @PathVariable Long adresseId,
                                                                @RequestBody AdresseLivraisonCreateDTO dto) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(authEnhancedService.modifierAdresse(userId, adresseId, dto));
    }

    @DeleteMapping("/api/users/adresses/{adresseId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> supprimerAdresse(@AuthenticationPrincipal UserDetails userDetails,
                                                  @PathVariable Long adresseId) {
        Long userId = getUserId(userDetails);
        authEnhancedService.supprimerAdresse(userId, adresseId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/users/adresses/{adresseId}/default")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AdresseLivraisonDTO> setAdresseParDefaut(@AuthenticationPrincipal UserDetails userDetails,
                                                                    @PathVariable Long adresseId) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(authEnhancedService.definirAdresseParDefaut(userId, adresseId));
    }

    // ===== RGPD =====

    @DeleteMapping("/api/users/compte")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> supprimerMonCompte(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        authEnhancedService.supprimerCompte(userId);
        return ResponseEntity.ok(Map.of("message", "Votre compte a été supprimé conformément au RGPD"));
    }

    private Long getUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
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
