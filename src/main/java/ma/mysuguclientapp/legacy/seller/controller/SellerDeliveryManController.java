package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.admin.LivreurDetailDTO;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.OrderSellerMapper;
import ma.mysuguclientapp.legacy.seller.mapper.SellerDeliveryManMapper;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.GainsLivreurRepository;
import ma.mysuguclientapp.services.interfaces.AdminLivreurService;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
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
    private final GainsLivreurRepository gainsLivreurRepository;
    private final AdminLivreurService adminLivreurService;
    private final CommandeService commandeService;
    private final OrderSellerMapper orderMapper;
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

    // ---- 3e.3: details / order-list / earning (derived, read-only, roster-guarded) ----

    /**
     * GET delivery-man/details/{id} : roster-guarded read-only detail via
     * {@link AdminLivreurService#getLivreurDetails}. A livreur id NOT in my derived roster (or
     * unknown id) -> benign neutral object, NEVER 404/500 (spec §3 row #4 — no leak of an
     * arbitrary livreur to a vendor who never worked with them).
     */
    @GetMapping("/delivery-man/details/{id}")
    public Map<String, Object> details(@AuthenticationPrincipal String email, @PathVariable Long id) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        if (!isInRoster(id, restaurant.getId())) {
            return mapper.toDetailsNeutral();
        }
        try {
            LivreurDetailDTO dto = adminLivreurService.getLivreurDetails(id);
            return mapper.toDetails(dto);
        } catch (RuntimeException e) {
            // Never 404/500 on this screen (spec §5) — benign neutral fallback.
            return mapper.toDetailsNeutral();
        }
    }

    /**
     * GET delivery-man/order-list/{id} : DERIVED — commandes this livreur delivered for MY
     * restaurant, reusing {@link OrderSellerMapper#toOrder} (the SAME order shape the vendor
     * app's own order list uses — {@code order_model.dart} — confirmed 3e.0;
     * NOT {@code LegacyOrderMapper}, which targets the livreur app's own order model). A livreur
     * who never served my restaurant -> empty list (never 404/500).
     */
    @GetMapping("/delivery-man/order-list/{id}")
    public Map<String, Object> orderList(@AuthenticationPrincipal String email, @PathVariable Long id,
                                          @RequestParam(defaultValue = "10") int limit,
                                          @RequestParam(defaultValue = "0") int offset) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        List<Commande> commandes = commandeRepository.findByLivreurAndRestaurant(id, restaurant.getId());
        List<Map<String, Object>> orders = commandes.stream()
                .map(c -> {
                    try {
                        CommandeDTO dto = commandeService.getCommandeById(c.getId());
                        return orderMapper.toOrder(dto);
                    } catch (RuntimeException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return mapper.toOrderListEnvelope(orders, limit, offset);
    }

    /**
     * GET delivery-man/earning/{id} : DERIVE-MINIMAL — sum {@code GainsLivreur.montantNet} over
     * this livreur's commandes belonging to MY restaurant only (cheap single JPQL join, see
     * {@link GainsLivreurRepository#sumMontantNetByLivreurAndRestaurant}). DEVIATION (3e.0): real
     * keys are {@code total_earn}/{@code withdrawable_balance}, not the spec's initially assumed
     * {@code total_earning}/{@code cash_in_hands}. Numbers never null. A livreur who never served
     * my restaurant -> zeros (never 404/500).
     */
    @GetMapping("/delivery-man/earning/{id}")
    public Map<String, Object> earning(@AuthenticationPrincipal String email, @PathVariable Long id) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        BigDecimal total = gainsLivreurRepository.sumMontantNetByLivreurAndRestaurant(id, restaurant.getId());
        return mapper.toEarning(total);
    }

    // ---- 3e.4: STUB management mutations (success-no-op) ----
    // STUB: vendors do not own livreurs in mysugu (umbrella §4 GAP; 3e SCOPE DECISION). No write
    // ever touches User/GainsLivreur/CaisseLivreur here. Multipart bodies (store/update, real app
    // uses http.MultipartRequest) are intentionally NOT bound — any body is accepted and ignored.

    /** POST delivery-man/store (multipart) : STUB — mysugu has no native livreur create (even admin). */
    @PostMapping("/delivery-man/store")
    public Map<String, Object> store(@AuthenticationPrincipal String email) {
        sellerContext.requireOwner(email);
        return mapper.success("Livreur ajouté.");
    }

    /**
     * POST delivery-man/update / delivery-man/update/{id} (multipart, {@code _method:put}) : STUB
     * — mysugu has no native livreur update. DEVIATION (3e.0): the real app posts to
     * {@code /delivery-man/update/{id}} (id in the URL, via {@code addNewDeliveryMan(isUpdate:
     * true)}), not bare {@code /delivery-man/update} as the spec initially assumed — both mapped.
     */
    @PostMapping({"/delivery-man/update", "/delivery-man/update/{id}"})
    public Map<String, Object> update(@AuthenticationPrincipal String email,
                                       @PathVariable(required = false) Long id) {
        sellerContext.requireOwner(email);
        return mapper.success("Livreur mis à jour.");
    }

    /**
     * GET|DELETE|POST delivery-man/delete/{id} : STUB — admin can soft-delete, vendor cannot.
     * DEVIATION (3e.0): the real app calls this with a plain GET ({@code dioClient!.get(...)}, no
     * {@code _method} spoofing) — GET is mapped alongside DELETE/POST (established _method-spoof
     * convention) so the real call never 404s/405s. Never deletes the global livreur.
     */
    @RequestMapping(value = {"/delivery-man/delete/{id}", "/delivery-man/delete/{id}/"},
            method = {RequestMethod.GET, RequestMethod.DELETE, RequestMethod.POST})
    public Map<String, Object> delete(@AuthenticationPrincipal String email, @PathVariable Long id) {
        sellerContext.requireOwner(email);
        return mapper.success("Livreur supprimé.");
    }

    /** POST delivery-man/cash-receive {deliveryman_id, amount} : STUB — cash reconciliation is admin CaisseController. */
    @PostMapping("/delivery-man/cash-receive")
    public Map<String, Object> cashReceive(@AuthenticationPrincipal String email) {
        sellerContext.requireOwner(email);
        return mapper.success("Espèces reçues.");
    }

    /** POST delivery-man/status-update {id, status} : STUB — admin toggles livreur active/availability, vendor cannot. */
    @PostMapping("/delivery-man/status-update")
    public Map<String, Object> statusUpdate(@AuthenticationPrincipal String email) {
        sellerContext.requireOwner(email);
        return mapper.success("Statut mis à jour.");
    }

    // ---- 3e.5: STUB lists + withdraw (benign empty / no-op) ----
    // STUB: vendors do not own livreurs in mysugu (umbrella §4 GAP; 3e SCOPE DECISION).

    /** GET delivery-man/reviews/{id} : STUB — no per-vendor livreur reviews natively. */
    @GetMapping("/delivery-man/reviews/{id}")
    public Map<String, Object> reviews(@AuthenticationPrincipal String email, @PathVariable Long id) {
        sellerContext.requireOwner(email);
        return mapper.reviewsStub();
    }

    /**
     * GET delivery-man/order-status-history/{id} : STUB — no such per-vendor history natively.
     * DEVIATION (3e.0): a BARE JSON ARRAY ({@code deliveryServiceInterface
     * .getDeliverymanOrderHistoryLog} does {@code apiResponse.response!.data.forEach(...)}), not
     * an envelope.
     */
    @GetMapping("/delivery-man/order-status-history/{id}")
    public List<Map<String, Object>> orderStatusHistory(@AuthenticationPrincipal String email, @PathVariable Long id) {
        sellerContext.requireOwner(email);
        return List.of();
    }

    /** GET delivery-man/collect-cash-list/{id} : STUB — cash collection is self-livreur/admin scoped. */
    @GetMapping("/delivery-man/collect-cash-list/{id}")
    public Map<String, Object> collectCashList(@AuthenticationPrincipal String email, @PathVariable Long id,
                                                @RequestParam(defaultValue = "10") int limit,
                                                @RequestParam(defaultValue = "0") int offset) {
        sellerContext.requireOwner(email);
        return mapper.collectCashListStub();
    }

    /** GET delivery-man/withdraw/list?status= : STUB — DemandeRetrait is admin-approved, not vendor-scoped. */
    @GetMapping("/delivery-man/withdraw/list")
    public Map<String, Object> withdrawList(@AuthenticationPrincipal String email,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "10") int limit,
                                             @RequestParam(defaultValue = "0") int offset) {
        sellerContext.requireOwner(email);
        return mapper.withdrawListStub();
    }

    /** GET delivery-man/withdraw/details/{id} : STUB — benign neutral, never 404/500. */
    @GetMapping("/delivery-man/withdraw/details/{id}")
    public Map<String, Object> withdrawDetails(@AuthenticationPrincipal String email, @PathVariable Long id) {
        sellerContext.requireOwner(email);
        return mapper.withdrawDetailsStub();
    }

    /**
     * POST delivery-man/withdraw/status-update {_method:put, id, note, approved} : STUB —
     * vendor CANNOT approve/refuse a retrait (admin-only via AdminLivreursController). Never
     * touches DemandeRetraitRepository.
     */
    @PostMapping("/delivery-man/withdraw/status-update")
    public Map<String, Object> withdrawStatusUpdate(@AuthenticationPrincipal String email) {
        sellerContext.requireOwner(email);
        return mapper.success("Statut de la demande de retrait mis à jour.");
    }

    // ---- helpers ----

    private boolean isInRoster(Long livreurId, Long restaurantId) {
        if (livreurId == null) {
            return false;
        }
        return commandeRepository.findDistinctLivreursByRestaurant(restaurantId).stream()
                .anyMatch(u -> u.getId().equals(livreurId));
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
