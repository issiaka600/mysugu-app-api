package ma.mysuguclientapp.legacy.seller.mapper;

import ma.mysuguclientapp.entities.User;
import org.springframework.stereotype.Component;

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

    private static int toInt(Boolean b) {
        return Boolean.TRUE.equals(b) ? 1 : 0;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
