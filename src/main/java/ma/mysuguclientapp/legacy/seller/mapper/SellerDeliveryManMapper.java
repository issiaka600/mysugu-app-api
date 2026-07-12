package ma.mysuguclientapp.legacy.seller.mapper;

import ma.mysuguclientapp.dtos.admin.LivreurDetailDTO;
import ma.mysuguclientapp.entities.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates the DERIVED vendor delivery-man roster (distinct {@code Commande.livreur} where
 * {@code restaurant = mine}, see {@code CommandeRepository#findDistinctLivreursByRestaurant})
 * into the 6valley shapes consumed by Tiktak-vendor-app-moso's Delivery-Man screens. Vendor to
 * livreur ownership is NOT native in mysugu (umbrella §4 GAP; 3e SCOPE DECISION) — every field
 * here is either a direct read-only mapping of the global {@link User}(LIVREUR) or a benign
 * default. See docs/superpowers/specs/2026-07-10-vendor-3e-deliveryman-design.md.
 *
 * <p><b>Deviations confirmed 3e.0 (real Dart app wins over the spec's initial assumptions):</b>
 * <ul>
 *   <li>{@code GET /seller-delivery-man} -> a BARE JSON ARRAY (the app does
 *       {@code apiResponse.response!.data.forEach(...)}), NOT the
 *       {@code {total_size,...,delivery_men:[]}} envelope the spec assumed. Items are parsed by
 *       {@code DeliveryManModel.fromJson}, which does
 *       {@code int.parse(json['seller_id'].toString())} WITHOUT a null guard -> {@code seller_id}
 *       must NEVER be null (mapped here to the vendor's restaurant id, an approximation — no real
 *       "seller_id" exists for a global livreur). {@code is_active} is assigned directly into a
 *       Dart {@code int?} field -> must be sent as 0/1, never a JSON boolean.</li>
 *   <li>{@code GET /delivery-man/list} -> envelope key is {@code delivery_man} (SINGULAR — same
 *       envelope class, {@code TopDeliveryManModel}, as {@code /top-delivery-man}), not
 *       {@code delivery_men}. Items require {@code is_online} as a non-null int
 *       ({@code int.parse(json['is_online'].toString())}, no null guard) and
 *       {@code identity_image} as a JSON-ENCODED STRING ({@code jsonDecode(json['identity_image'])}
 *       with no null guard) — never omitted/null.</li>
 * </ul>
 */
@Component
public class SellerDeliveryManMapper {

    /** GET /seller-delivery-man item (bare array, DeliveryManModel shape, 3e.0 deviation). */
    public Map<String, Object> toRosterItem(User livreur, Long restaurantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", livreur.getId());
        // FRAGILE: int.parse(json['seller_id'].toString()) with no null guard (3e.0) — mysugu has
        // no real "seller_id" for a global livreur; the vendor's own restaurant id stands in.
        m.put("seller_id", restaurantId != null ? restaurantId : 0L);
        m.put("f_name", nvl(livreur.getPrenom()));
        m.put("l_name", nvl(livreur.getNom()));
        m.put("phone", nvl(livreur.getTelephone()));
        m.put("email", nvl(livreur.getEmail()));
        m.put("identity_number", "");
        m.put("identity_type", "");
        m.put("identity_image", "");
        m.put("image", nvl(livreur.getAvatar()));
        m.put("is_active", toInt(livreur.getIsActive())); // FRAGILE: int?, never a JSON boolean (3e.0)
        m.put("created_at", str(livreur.getCreatedAt()));
        m.put("updated_at", str(livreur.getUpdatedAt()));
        m.put("fcm_token", "");
        return m;
    }

    public List<Map<String, Object>> toRosterArray(List<User> livreurs, Long restaurantId) {
        return livreurs.stream().map(l -> toRosterItem(l, restaurantId)).toList();
    }

    /** GET /delivery-man/list item (TopDeliveryManModel's DeliveryMan shape — shared with top-delivery-man). */
    public Map<String, Object> toListItem(User livreur) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", livreur.getId());
        m.put("f_name", nvl(livreur.getPrenom()));
        m.put("l_name", nvl(livreur.getNom()));
        m.put("address", "");
        m.put("country_code", nvl(livreur.getCountryCode()));
        m.put("phone", nvl(livreur.getTelephone()));
        m.put("email", nvl(livreur.getEmail()));
        m.put("identity_number", "");
        m.put("identity_type", "");
        // FRAGILE: jsonDecode(json['identity_image']) with NO null guard (3e.0) -> must be a
        // JSON-encoded STRING (e.g. "[]"), never null/omitted.
        m.put("identity_image", "[]");
        m.put("image", nvl(livreur.getAvatar()));
        // FRAGILE: int.parse(json['is_online'].toString()) with NO null guard (3e.0) -> never null.
        m.put("is_online", toInt(livreur.getLivreurDisponible()));
        return m;
    }

    /** GET /delivery-man/list envelope: {total_size, limit, offset, delivery_man:[...]} (3e.0 key). */
    public Map<String, Object> toListEnvelope(List<User> livreurs, int limit, int offset) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", livreurs.size());
        m.put("limit", String.valueOf(limit));
        m.put("offset", String.valueOf(offset));
        m.put("delivery_man", livreurs.stream().map(this::toListItem).toList());
        return m;
    }

    // ---- 3e.3: details / order-list / earning (derived, read-only, roster-guarded) ----

    /**
     * GET /delivery-man/details/{id} -> {delivery_man, withdrawbale_balance} (6valley typo kept
     * verbatim, 3e.0). Only called for a livreur IN the roster (guarded by the controller) —
     * read-only mapping of {@link ma.mysuguclientapp.services.interfaces.AdminLivreurService
     * #getLivreurDetails} money fields into the app's "wallet" shape.
     */
    public Map<String, Object> toDetails(LivreurDetailDTO dto) {
        Map<String, Object> deliveryMan = new LinkedHashMap<>();
        deliveryMan.put("id", dto.getId());
        deliveryMan.put("f_name", nvl(dto.getPrenom()));
        deliveryMan.put("l_name", nvl(dto.getNom()));
        deliveryMan.put("address", "");
        deliveryMan.put("country_code", "");
        deliveryMan.put("phone", nvl(dto.getTelephone()));
        deliveryMan.put("email", nvl(dto.getEmail()));
        deliveryMan.put("image", nvl(dto.getAvatar()));
        deliveryMan.put("bank_name", "");
        deliveryMan.put("branch", "");
        deliveryMan.put("account_no", "");
        deliveryMan.put("holder_name", "");
        deliveryMan.put("is_active", toInt(dto.getIsActive())); // FRAGILE: int?, never boolean (3e.0)
        deliveryMan.put("is_online", toInt(dto.getLivreurDisponible()));

        Map<String, Object> wallet = new LinkedHashMap<>();
        wallet.put("id", dto.getId());
        wallet.put("delivery_man_id", dto.getId());
        wallet.put("current_balance", nz(dto.getSoldeActuel()));
        wallet.put("cash_in_hand", nz(dto.getEspeceEnCaisse()));
        wallet.put("pending_withdraw", nz(dto.getMontantEnAttenteRetrait()));
        wallet.put("total_withdraw", nz(dto.getTotalRetireApprouve()));
        wallet.put("created_at", str(dto.getCreatedAt()));
        wallet.put("updated_at", str(dto.getCreatedAt()));
        deliveryMan.put("wallet", wallet);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("delivery_man", deliveryMan);
        m.put("withdrawbale_balance", nz(dto.getSoldeActuel())); // sic: 6valley app-side typo, kept verbatim
        return m;
    }

    /** Benign neutral shape for a livreur id NOT in my roster (never 404/500 — spec §5). */
    public Map<String, Object> toDetailsNeutral() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("delivery_man", null);
        m.put("withdrawbale_balance", BigDecimal.ZERO);
        return m;
    }

    /** GET /delivery-man/order-list/{id} envelope: {total_size, limit, offset, orders:[...]}. */
    public Map<String, Object> toOrderListEnvelope(List<Map<String, Object>> orders, int limit, int offset) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", orders.size());
        m.put("limit", String.valueOf(limit));
        m.put("offset", String.valueOf(offset));
        m.put("orders", orders);
        return m;
    }

    /**
     * GET /delivery-man/earning/{id} -> DeliveryManEarningModel shape. DEVIATION (3e.0): the real
     * Dart model reads {@code total_earn}/{@code withdrawable_balance}, NOT the spec's assumed
     * {@code total_earning}/{@code cash_in_hands}. Numbers never null (spec §5).
     */
    public Map<String, Object> toEarning(BigDecimal totalEarn) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", 0);
        m.put("limit", "10");
        m.put("offset", "0");
        m.put("total_earn", nz(totalEarn));
        m.put("withdrawable_balance", nz(totalEarn));
        m.put("orders", List.of());
        return m;
    }

    // ---- 3e.4/3e.5: STUB envelopes (success-no-op / benign empty) ----
    // STUB: vendors do not own livreurs in mysugu (umbrella §4 GAP; 3e SCOPE DECISION).

    /** Generic success-no-op envelope for STUB mutations — HTTP 200 {"message": "..."}. */
    public Map<String, Object> success(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", message);
        return m;
    }

    private static int toInt(Boolean b) {
        return Boolean.TRUE.equals(b) ? 1 : 0;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
