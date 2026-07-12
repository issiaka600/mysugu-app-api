package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.SellerProfileMapper;
import ma.mysuguclientapp.services.interfaces.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Shop / profil du shim vendeur (contrat 6valley) : {@code seller-info}/{@code seller-update}
 * réutilisent {@code UserService} (profil du RESTAURANT_OWNER) ; {@code shop-info}/
 * {@code shop-update} réutilisent {@code RestaurantService} (le restaurant du vendeur, résolu
 * via {@link SellerContext} — jamais depuis un id du corps de requête). {@code temporary-close}
 * et {@code vacation-add} sont des GAP mappés sur {@code Restaurant.isActive} (voir méthodes
 * ajoutées dans les tranches 3b.5/3b.6). Voir
 * docs/superpowers/specs/2026-07-10-vendor-3b-shop-profile-design.md.
 */
@RestController
@RequestMapping("/api/v3/seller")
@RequiredArgsConstructor
@Slf4j
public class SellerShopProfileController {

    private final SellerContext sellerContext;
    private final UserService userService;
    private final SellerProfileMapper sellerProfileMapper;

    /** GET seller-info : profil du vendeur authentifié, forme 6valley. */
    @GetMapping("/seller-info")
    public Map<String, Object> sellerInfo(@AuthenticationPrincipal String email,
                                          @RequestHeader("Authorization") String authorization) {
        sellerContext.requireOwner(email); // 403 si non-vendeur
        UserDTO profile = userService.getProfile(authorization);
        return sellerProfileMapper.toSellerInfo(profile);
    }
}
