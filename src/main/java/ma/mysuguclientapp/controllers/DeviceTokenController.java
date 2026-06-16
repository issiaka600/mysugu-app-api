package ma.mysuguclientapp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.DeviceTokenRegisterDTO;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.services.interfaces.DeviceTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/device-tokens")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Enregistre ou met à jour un token FCM pour un utilisateur.
     * À appeler depuis l'application mobile au démarrage ou lors du renouvellement du token.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerToken(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody DeviceTokenRegisterDTO dto
    ) {
        String jwt = authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : authorization;
        Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
        deviceTokenService.registerToken(userId, dto.getToken(), dto.getPlatform());
        return ResponseEntity.ok(Map.of("message", "Token FCM enregistré avec succès"));
    }

    /**
     * Désactive un token FCM (à appeler lors de la déconnexion).
     */
    @DeleteMapping("/{token}")
    public ResponseEntity<Map<String, String>> deactivateToken(@PathVariable String token) {
        deviceTokenService.deactivateToken(token);
        return ResponseEntity.ok(Map.of("message", "Token FCM désactivé"));
    }

    /**
     * Désactive tous les tokens FCM d'un utilisateur.
     */
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Map<String, String>> deactivateAllTokensForUser(@PathVariable Long userId) {
        deviceTokenService.deactivateAllTokensForUser(userId);
        return ResponseEntity.ok(Map.of("message", "Tous les tokens FCM de l'utilisateur désactivés"));
    }
}
