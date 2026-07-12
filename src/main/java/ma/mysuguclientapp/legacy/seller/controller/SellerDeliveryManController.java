package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.SellerDeliveryManMapper;
import ma.mysuguclientapp.repositories.CommandeRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Vendor delivery-man shim (6valley contract): {@code /api/v3/seller/seller-delivery-man},
 * {@code /api/v3/seller/delivery-man/*}. SCOPE DECISION (3e): the 6valley model "the vendor
 * manages THEIR delivery-men" does NOT exist in mysugu — livreurs are GLOBAL {@link User}s (role
 * LIVREUR) with NO restaurant/owner FK. All real livreur management stays ADMIN-only
 * ({@code AdminLivreursController}). This controller therefore:
 * <ol>
 *   <li>DERIVES, read-only, the "roster" of livreurs who actually served the vendor's restaurant
 *       (distinct {@code Commande.livreur} where {@code restaurant = mine}) and that livreur's
 *       orders/earnings scoped to my restaurant — see {@link CommandeRepository}.</li>
 *   <li>STUBs every write/management endpoint with a benign 6valley shape (success-no-op or
 *       empty list) so the app screen renders and NEVER hard-crashes/404s/500s on a list.</li>
 * </ol>
 * {@code /api/v3/seller/top-delivery-man} is intentionally NOT here — already served by
 * {@code SellerStatsController} (slice 3j). Real vendor->livreur ownership needs product-owner
 * sign-off (umbrella §4 GAP; see spec §7) — do NOT build it under cover of this slice.
 * See docs/superpowers/specs/2026-07-10-vendor-3e-deliveryman-design.md.
 */
@RestController
@RequestMapping("/api/v3/seller")
@RequiredArgsConstructor
public class SellerDeliveryManController {

    private final SellerContext sellerContext;
    private final CommandeRepository commandeRepository;
    private final SellerDeliveryManMapper mapper;

    // ---- 3e.2: roster (derived, read-only) ----

    /**
     * GET seller-delivery-man : DERIVED roster (distinct livreurs who served MY restaurant).
     * DEVIATION (3e.0): bare JSON ARRAY, not an envelope — {@code DeliveryManRepository
     * .getDeliveryManList} does {@code apiResponse.response!.data.forEach(...)}. Empty roster ->
     * {@code []} (never 404/500).
     */
    @GetMapping("/seller-delivery-man")
    public List<Map<String, Object>> sellerDeliveryMan(@AuthenticationPrincipal String email) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        List<User> roster = commandeRepository.findDistinctLivreursByRestaurant(restaurant.getId());
        return mapper.toRosterArray(roster, restaurant.getId());
    }

    /**
     * GET delivery-man/list?search= : same DERIVED roster as {@link #sellerDeliveryMan}, paginated
     * envelope variant ({@code TopDeliveryManModel} shape, key {@code delivery_man} — 3e.0). Empty
     * roster -> {@code delivery_man: []}, {@code total_size: 0} (never 404).
     */
    @GetMapping("/delivery-man/list")
    public Map<String, Object> deliveryManList(@AuthenticationPrincipal String email,
                                                @RequestParam(defaultValue = "10") int limit,
                                                @RequestParam(defaultValue = "0") int offset,
                                                @RequestParam(required = false) String search) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        List<User> roster = commandeRepository.findDistinctLivreursByRestaurant(restaurant.getId());
        if (search != null && !search.isBlank()) {
            String needle = search.trim().toLowerCase();
            roster = roster.stream()
                    .filter(l -> (nvl(l.getPrenom()) + " " + nvl(l.getNom())).toLowerCase().contains(needle)
                            || nvl(l.getEmail()).toLowerCase().contains(needle)
                            || nvl(l.getTelephone()).toLowerCase().contains(needle))
                    .toList();
        }
        return mapper.toListEnvelope(roster, limit, offset);
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
