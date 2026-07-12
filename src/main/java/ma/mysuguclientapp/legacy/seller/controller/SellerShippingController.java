package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Coût d'expédition par catégorie / type d'expédition sélectionnable du shim vendeur (contrat
 * 6valley) : {@code /api/v3/seller/shipping/*}. mysugu est du meal-delivery tarifé à la distance
 * (voir {@code ZoneDeploiement}, admin, non vendeur, sans coût par catégorie de produit) : il
 * n'existe AUCUN coût d'expédition par catégorie ni type d'expédition sélectionnable par le
 * vendeur ({@code order_wise} / {@code product_wise} / {@code category_wise}). TRANCHE
 * ENTIÈREMENT STUB — aucun modèle/service/entité natif créé ou touché. Chaque endpoint répond
 * 200 avec une forme bénigne tolérée par l'app, jamais 404/500. Formes confirmées 3i.0 contre
 * Tiktak-vendor-app-moso (shipping_controller.dart / category_wise_shipping_model.dart) :
 * <ul>
 *   <li>{@code all-category-cost} : la clé {@code all_category_shipping_cost} DOIT être une
 *   liste (même vide) — {@code CategoryWiseShippingModel.fromJson(...).allCategoryShippingCost!}
 *   est force-unwrap côté app.</li>
 *   <li>{@code get-shipping-method} : la clé {@code type} DOIT exister — l'app lit
 *   {@code data['type']} et bascule l'onglet sélectionné en conséquence ; on renvoie
 *   {@code order_wise} comme défaut neutre (onglet 0).</li>
 *   <li>{@code set-category-cost}/{@code selected-shipping-method} : accusé bénin no-op, l'app
 *   ne vérifie que le status HTTP 200 (corps ignoré).</li>
 * </ul>
 * Voir docs/superpowers/specs/2026-07-10-vendor-3i-shipping-design.md.
 */
@RestController
@RequestMapping("/api/v3/seller/shipping")
@RequiredArgsConstructor
public class SellerShippingController {

    private final SellerContext sellerContext;

    private Map<String, Object> ok() {
        return Map.of("message", "Not applicable for meal delivery.");
    }

    // STUB: no per-vendor shipping in meal delivery (umbrella §4 GAP)
    @GetMapping({"/all-category-cost", "/all-category-cost/"})
    public Map<String, Object> allCategoryCost(@AuthenticationPrincipal String email) {
        sellerContext.requireOwner(email);
        return Map.of("all_category_shipping_cost", List.of()); // STUB: key must exist, force-unwrapped by the app
    }

    // STUB: no per-vendor shipping in meal delivery (umbrella §4 GAP)
    @PostMapping({"/set-category-cost", "/set-category-cost/"})
    public Map<String, Object> setCategoryCost(@AuthenticationPrincipal String email,
                                                @RequestBody(required = false) Map<String, Object> body) {
        sellerContext.requireOwner(email);
        return ok(); // STUB no-op, nothing persisted
    }

    // STUB: no per-vendor shipping in meal delivery (umbrella §4 GAP)
    @GetMapping({"/selected-shipping-method", "/selected-shipping-method/"})
    public Map<String, Object> selectedShippingMethod(@AuthenticationPrincipal String email,
                                                        @RequestParam(value = "shipping_type", required = false) String shippingType) {
        sellerContext.requireOwner(email);
        return ok(); // STUB no-op; app only checks HTTP 200, body unused
    }

    // STUB: no per-vendor shipping in meal delivery (umbrella §4 GAP)
    @GetMapping({"/get-shipping-method", "/get-shipping-method/"})
    public Map<String, Object> getShippingMethod(@AuthenticationPrincipal String email) {
        sellerContext.requireOwner(email);
        return Map.of("type", "order_wise"); // STUB: single default; app reads data['type']
    }
}
