package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Méthodes d'expédition du shim vendeur (contrat 6valley) : {@code /api/v3/seller/shipping-method/*}.
 * mysugu est du meal-delivery tarifé à la distance (voir {@code ZoneDeploiement}, admin, non
 * vendeur) : il n'existe AUCUNE méthode d'expédition propre au vendeur (pas de "Standard /
 * Express, coût X, délai Y"). TRANCHE ENTIÈREMENT STUB — aucun modèle/service/entité natif créé
 * ou touché. Chaque endpoint répond 200 avec une forme bénigne tolérée par l'app, jamais 404/500.
 * Formes confirmées 3i.0 contre Tiktak-vendor-app-moso (shipping_controller.dart /
 * shipping_model.dart) :
 * <ul>
 *   <li>{@code list} : tableau JSON nu vide (l'app itère {@code data.forEach}) — jamais
 *   d'enveloppe.</li>
 *   <li>{@code edit} : objet bénin, {@code status} DOIT être un booléen JSON réel (pas 0/1) et
 *   {@code cost} non-null ({@code ShippingModel.fromJson}). Route définie dans
 *   app_constants.dart mais non invoquée par le repository actuel — fournie quand même pour ne
 *   jamais 404.</li>
 *   <li>{@code add}/{@code update}/{@code delete}/{@code status} : accusé bénin
 *   {@code {"message": ...}}, no-op — rien n'est persisté.</li>
 * </ul>
 * Voir docs/superpowers/specs/2026-07-10-vendor-3i-shipping-design.md.
 */
@RestController
@RequestMapping("/api/v3/seller/shipping-method")
@RequiredArgsConstructor
public class SellerShippingMethodController {

    private final SellerContext sellerContext;

    private Map<String, Object> ok() {
        return Map.of("message", "Not applicable for meal delivery.");
    }

    // STUB: no per-vendor shipping in meal delivery (umbrella §4 GAP)
    @GetMapping({"/list", "/list/"})
    public List<Object> list(@AuthenticationPrincipal String email) {
        sellerContext.requireOwner(email);
        return List.of(); // STUB: raw empty array — app iterates data.forEach
    }

    // STUB: no per-vendor shipping in meal delivery (umbrella §4 GAP)
    @PostMapping({"/add", "/add/"})
    public Map<String, Object> add(@AuthenticationPrincipal String email,
                                    @RequestBody(required = false) Map<String, Object> body) {
        sellerContext.requireOwner(email);
        return ok(); // STUB no-op, nothing persisted
    }

    // STUB: no per-vendor shipping in meal delivery (umbrella §4 GAP)
    @PostMapping({"/update/{id}", "/update/{id}/"})
    public Map<String, Object> update(@AuthenticationPrincipal String email,
                                       @PathVariable Long id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        sellerContext.requireOwner(email);
        return ok(); // STUB no-op, nothing persisted
    }

    // STUB: no per-vendor shipping in meal delivery (umbrella §4 GAP)
    @GetMapping({"/edit", "/edit/"})
    public Map<String, Object> edit(@AuthenticationPrincipal String email) {
        sellerContext.requireOwner(email);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", 0);
        m.put("title", "");
        m.put("duration", "");
        m.put("cost", 0);
        m.put("status", false); // STUB: status must be a real JSON boolean per ShippingModel.fromJson
        return m;
    }

    // STUB: no per-vendor shipping in meal delivery (umbrella §4 GAP)
    @DeleteMapping({"/delete/{id}", "/delete/{id}/"})
    public Map<String, Object> delete(@AuthenticationPrincipal String email, @PathVariable Long id) {
        sellerContext.requireOwner(email);
        return ok(); // STUB no-op, nothing persisted
    }

    // STUB: no per-vendor shipping in meal delivery (umbrella §4 GAP)
    @PostMapping({"/status", "/status/"})
    public Map<String, Object> status(@AuthenticationPrincipal String email,
                                       @RequestBody(required = false) Map<String, Object> body) {
        sellerContext.requireOwner(email);
        return ok(); // STUB no-op, nothing persisted
    }
}
