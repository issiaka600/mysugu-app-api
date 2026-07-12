package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.dto.MessageResponse;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.AuthEnhancedService;
import ma.mysuguclientapp.services.interfaces.DeviceTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Compte / appareil du shim vendeur (contrat 6valley). Toutes ces routes sont authentifiées
 * RESTAURANT_OWNER : le rôle est vérifié par {@link SellerContext#requireOwner}. Les "PUT*"
 * arrivent en POST avec {@code _method:put} dans le corps -> mappés en @PostMapping.
 *
 * <ul>
 *   <li>{@code POST /cm-firebase-token} : enregistre le token FCM du vendeur (réutilise le
 *       service natif {@code DeviceTokenService.registerToken}).</li>
 *   <li>{@code GET /account-delete} : suppression RGPD (réutilise {@code AuthEnhancedService.supprimerCompte}).</li>
 *   <li>{@code POST /language-change} : persiste {@code User.appLanguage}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v3/seller")
@RequiredArgsConstructor
@Slf4j
public class SellerAccountController {

    private final SellerContext sellerContext;
    private final UserRepository userRepository;
    private final DeviceTokenService deviceTokenService;
    private final AuthEnhancedService authEnhancedService;

    /** PUT* cm-firebase-token. {@code cm_firebase_token == "no"} (logout) désactive les tokens. */
    @PostMapping("/cm-firebase-token")
    public MessageResponse registerFcmToken(@AuthenticationPrincipal String email,
                                            @RequestBody Map<String, Object> body) {
        User owner = sellerContext.requireOwner(email);
        String token = str(body.get("cm_firebase_token"));
        if (token == null || token.isBlank() || "no".equalsIgnoreCase(token)) {
            deviceTokenService.deactivateAllTokensForUser(owner.getId());
        } else {
            deviceTokenService.registerToken(owner.getId(), token, "android");
        }
        return new MessageResponse("Token FCM enregistré avec succès.");
    }

    /** account-delete : suppression RGPD du compte vendeur (anonymisation + désactivation). */
    @GetMapping("/account-delete")
    public ResponseEntity<?> accountDelete(@AuthenticationPrincipal String email) {
        User owner = sellerContext.requireOwner(email);
        authEnhancedService.supprimerCompte(owner.getId());
        log.info("Compte vendeur supprimé (RGPD) pour {}", email);
        return ResponseEntity.ok(Map.of("message", "Compte supprimé avec succès."));
    }

    /** PUT* language-change {current_language}. */
    @PostMapping("/language-change")
    public MessageResponse languageChange(@AuthenticationPrincipal String email,
                                          @RequestBody Map<String, Object> body) {
        User owner = sellerContext.requireOwner(email);
        owner.setAppLanguage(str(body.get("current_language")));
        userRepository.save(owner);
        return new MessageResponse("Langue mise à jour.");
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
