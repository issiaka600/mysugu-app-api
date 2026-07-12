package ma.mysuguclientapp.legacy.seller.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoints partagés 6valley appelés par l'app vendeur (et livreur) au boot mais sans équivalent
 * natif MySugu. On renvoie des formes bénignes non-null que l'app tolère, JAMAIS 500/404, pour ne
 * pas bloquer le démarrage ni la navigation. Voir docs/superpowers/specs/2026-07-10-vendor-3a-auth-design.md §2.
 *
 * <p>{@code /api/v1/config} est fourni par {@code ConfigLegacyController} (non stub).</p>
 */
@RestController
@RequestMapping("/api/v1")
public class SellerSharedController {

    /** Attributs produits 6valley : aucun équivalent natif -> liste vide (pas de filtre attribut). */
    @GetMapping("/attributes")
    public List<Object> attributes() {
        // STUB: no native equivalent (umbrella §4 GAP handling)
        return List.of();
    }

    /** Sous-catégories hiérarchiques 6valley : aucun équivalent natif -> liste vide. */
    @GetMapping({"/categories/childes/", "/categories/childes/{id}"})
    public List<Object> childes() {
        // STUB: no native equivalent (umbrella §4 GAP handling)
        return List.of();
    }

    /** Sous-sous-catégories hiérarchiques 6valley : aucun équivalent natif -> liste vide. */
    @GetMapping({"/categories/childes/childes/", "/categories/childes/childes/{id}"})
    public List<Object> childesChildes() {
        // STUB: no native equivalent (umbrella §4 GAP handling)
        return List.of();
    }

    /** Proxy Google Maps 6valley (geocode / autocomplete / details) : aucun proxy natif ->
     *  réponse ZERO_RESULTS vide que l'app tolère (pas d'autocomplétion d'adresse). */
    @GetMapping({"/mapapi/geocode-api", "/mapapi/place-api-autocomplete", "/mapapi/place-api-details"})
    public Map<String, Object> mapapi() {
        // STUB: no native equivalent (umbrella §4 GAP handling)
        return Map.of("status", "ZERO_RESULTS", "results", List.of());
    }
}
