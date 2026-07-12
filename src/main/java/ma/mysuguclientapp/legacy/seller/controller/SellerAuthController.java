package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.seller.dto.ErrorsResponse;
import ma.mysuguclientapp.legacy.seller.dto.TokenResponse;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

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

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
