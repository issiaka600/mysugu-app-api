package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.ContactUrgenceCreateDTO;
import ma.mysuguclientapp.dtos.ContactUrgenceDTO;
import ma.mysuguclientapp.dtos.ContactUrgenceStatutUpdateDTO;
import ma.mysuguclientapp.entities.ContactUrgence;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.repositories.ContactUrgenceRepository;
import ma.mysuguclientapp.services.interfaces.ContactUrgenceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vendor emergency-contact shim (6valley contract): {@code /api/v3/seller/delivery-man
 * /emergency-contact/*}. Task 3e.6 — the ONE part of the delivery-man slice that is genuinely
 * REAL, not a stub: {@link ContactUrgenceService} (native {@link ContactUrgence}) already models
 * per-restaurant emergency contacts (nullable {@code restaurant} = global platform contact).
 * Every mutation is scoped to {@code restaurant = mine}: a vendor may only create/read/update/
 * delete contacts belonging to THEIR OWN restaurant. A cross-restaurant id -> benign no-op
 * (never a 500, never leaks/mutates another vendor's contact).
 *
 * <p><b>Deviations confirmed 3e.0 (real Dart app wins over the spec's initial assumptions):</b>
 * <ul>
 *   <li>{@code GET .../list} envelope key is {@code contact_list} (the app does
 *       {@code _contactList.addAll(_emergencyContactModel.contactList!)} — a FORCE UNWRAP, so
 *       {@code contact_list} must ALWAYS be present as an array, never omitted/null), NOT
 *       {@code emergency_contacts} as the spec initially assumed.</li>
 *   <li>{@code ContactList.fromJson} does {@code json['status'] ? 1 : 0} WITHOUT a null guard ->
 *       {@code status} must ALWAYS be a JSON boolean, never null/absent.</li>
 *   <li>{@code POST .../delete} : the real app posts the id in the BODY ({@code {_method:
 *       'delete', id}}), NOT a path variable {@code /delete/{id}} as the spec initially assumed —
 *       {@code emergencyContactDelete} has no trailing id segment in {@code app_constants.dart}.</li>
 *   <li>{@code POST .../update} similarly takes {@code id} in the body, not the path (repo call
 *       posts to the SAME {@code emergencyContactUpdate} URL for both create/update, switching
 *       only {@code _method}).</li>
 * </ul>
 * See docs/superpowers/specs/2026-07-10-vendor-3e-deliveryman-design.md §3 rows #18-22.
 */
@RestController
@RequestMapping("/api/v3/seller/delivery-man/emergency-contact")
@RequiredArgsConstructor
public class SellerEmergencyContactController {

    private final SellerContext sellerContext;
    private final ContactUrgenceRepository contactUrgenceRepository;
    private final ContactUrgenceService contactUrgenceService;

    /**
     * GET list : contacts of MY restaurant (via {@link ContactUrgenceService#getAll}). DEVIATION
     * (3e.0): envelope key is {@code contact_list} (force-unwrapped by the app), never omitted.
     * Empty -> {@code contact_list: []} (never 404).
     */
    @GetMapping("/list")
    public Map<String, Object> list(@AuthenticationPrincipal String email) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        List<ContactUrgenceDTO> contacts = contactUrgenceService.getAll(restaurant.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contact_list", contacts.stream().map(this::toContactListItem).toList());
        return m;
    }

    /** POST store {name, phone} : creates a ContactUrgence with restaurant = MY restaurant. */
    @PostMapping("/store")
    public Map<String, Object> store(@AuthenticationPrincipal String email,
                                      @RequestBody(required = false) Map<String, Object> body) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        ContactUrgenceCreateDTO dto = new ContactUrgenceCreateDTO();
        dto.setRestaurantId(restaurant.getId());
        dto.setNom(str(body, "name"));
        dto.setTelephone(str(body, "phone"));
        contactUrgenceService.create(dto);
        return success("Contact d'urgence ajouté.");
    }

    /**
     * POST update {id, name, phone, _method:put} : edits a contact of MY restaurant. DEVIATION
     * (3e.0): {@code id} comes from the BODY, not a path variable. A contact belonging to ANOTHER
     * restaurant -> benign no-op (200, no cross-restaurant mutation), never 500.
     */
    @PostMapping("/update")
    public Map<String, Object> update(@AuthenticationPrincipal String email,
                                       @RequestBody(required = false) Map<String, Object> body) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        Long id = toLong(body != null ? body.get("id") : null);
        ContactUrgence contact = ownedContactOrNull(id, restaurant.getId());
        if (contact == null) {
            return success("Contact d'urgence mis à jour.");
        }
        ContactUrgenceCreateDTO dto = new ContactUrgenceCreateDTO();
        dto.setRestaurantId(restaurant.getId());
        dto.setNom(str(body, "name"));
        dto.setTelephone(str(body, "phone"));
        contactUrgenceService.update(id, dto);
        return success("Contact d'urgence mis à jour.");
    }

    /**
     * POST status-update {id, status, _method:put} : toggles {@code actif} on a contact of MY
     * restaurant. A contact belonging to ANOTHER restaurant -> benign no-op, never 500.
     */
    @PostMapping("/status-update")
    public Map<String, Object> statusUpdate(@AuthenticationPrincipal String email,
                                             @RequestBody(required = false) Map<String, Object> body) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        Long id = toLong(body != null ? body.get("id") : null);
        ContactUrgence contact = ownedContactOrNull(id, restaurant.getId());
        if (contact == null) {
            return success("Statut mis à jour.");
        }
        Object statusRaw = body.get("status");
        boolean actif = statusRaw != null && ("1".equals(statusRaw.toString()) || "true".equalsIgnoreCase(statusRaw.toString()));
        ContactUrgenceStatutUpdateDTO dto = new ContactUrgenceStatutUpdateDTO();
        dto.setActif(actif);
        contactUrgenceService.updateStatut(id, dto);
        return success("Statut mis à jour.");
    }

    /**
     * POST delete {id, _method:delete} : deletes a contact of MY restaurant. DEVIATION (3e.0):
     * {@code id} comes from the BODY, not a path variable (no {@code /delete/{id}}). A contact
     * belonging to ANOTHER restaurant -> benign no-op, never 500.
     */
    @PostMapping("/delete")
    public Map<String, Object> delete(@AuthenticationPrincipal String email,
                                       @RequestBody(required = false) Map<String, Object> body) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        Long id = toLong(body != null ? body.get("id") : null);
        ContactUrgence contact = ownedContactOrNull(id, restaurant.getId());
        if (contact == null) {
            return success("Contact d'urgence supprimé.");
        }
        contactUrgenceService.delete(id);
        return success("Contact d'urgence supprimé.");
    }

    /** Loads the contact only if it belongs to {@code restaurantId}; else null (benign no-op path). */
    private ContactUrgence ownedContactOrNull(Long id, Long restaurantId) {
        if (id == null) {
            return null;
        }
        return contactUrgenceRepository.findById(id)
                .filter(c -> c.getRestaurant() != null && c.getRestaurant().getId().equals(restaurantId))
                .orElse(null);
    }

    /** ContactList shape (emergency_contact_model.dart). status MUST be a non-null JSON boolean (3e.0). */
    private Map<String, Object> toContactListItem(ContactUrgenceDTO dto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dto.getId());
        m.put("user_id", null);
        m.put("name", dto.getNom());
        m.put("phone", dto.getTelephone());
        m.put("status", Boolean.TRUE.equals(dto.getActif())); // FRAGILE: never null (3e.0)
        m.put("created_at", dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null);
        m.put("updated_at", dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> success(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", message);
        return m;
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return null;
        }
        return body.get(key).toString();
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
