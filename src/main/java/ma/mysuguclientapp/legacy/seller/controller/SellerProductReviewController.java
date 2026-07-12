package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.AvisDTO;
import ma.mysuguclientapp.dtos.ModerationAvisDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.StatutAvis;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.ProductSellerMapper;
import ma.mysuguclientapp.services.interfaces.AvisService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Avis produit du shim vendeur : {@code Avis} natif est au niveau restaurant (pas par produit),
 * donc {@code products/review-list/{id}} et {@code shop-product-reviews} sont des STUB à
 * enveloppe bénigne vide (jamais 404/500). {@code shop-product-reviews-status} délègue à la
 * modération native des avis ({@code AvisService.moderAvis}, même logique que
 * {@code PATCH /api/avis/{id}/moderation}). Voir
 * docs/superpowers/specs/2026-07-10-vendor-3c-products-design.md §3/§6.
 */
@RestController
@RequestMapping("/api/v3/seller")
@RequiredArgsConstructor
public class SellerProductReviewController {

    private final SellerContext sellerContext;
    private final AvisService avisService;
    private final ProductSellerMapper mapper;

    /**
     * GET products/review-list/{id} : avis d'un produit précis. // GAP: Avis is
     * restaurant-level, not per-product (umbrella §4).
     */
    @GetMapping("/products/review-list/{id}")
    public Map<String, Object> reviewList(@AuthenticationPrincipal String email,
                                           @PathVariable Long id,
                                           @RequestParam(defaultValue = "10") int limit,
                                           @RequestParam(defaultValue = "0") int offset) {
        sellerContext.currentRestaurant(email); // 403 non-vendeur / 404 sans restaurant
        return mapper.emptyReviewEnvelope(limit, offset);
    }

    /**
     * GET shop-product-reviews : avis de la boutique, vue "par produit". // GAP: Avis is
     * restaurant-level, not per-product (umbrella §4). Follow-up: surfacer les avis restaurant
     * si l'écran doit afficher du contenu.
     */
    @GetMapping("/shop-product-reviews")
    public Map<String, Object> shopProductReviews(@AuthenticationPrincipal String email,
                                                   @RequestParam(defaultValue = "10") int limit,
                                                   @RequestParam(defaultValue = "0") int offset) {
        sellerContext.currentRestaurant(email); // 403 non-vendeur / 404 sans restaurant
        return mapper.emptyReviewEnvelope(limit, offset);
    }

    /**
     * POST shop-product-reviews-status {id,status} : délègue à la modération native des avis
     * (EXISTS(avis) — umbrella §3). {@code status=1} => APPROUVE, {@code status=0} => REJETE
     * (raison bénigne fournie car {@code AvisService} l'exige pour un rejet). Appartenance
     * vérifiée via {@link #requireOwnedAvis} — {@code AvisService.moderAvis} natif n'a aucun
     * scoping restaurant, donc sans cette garde un vendeur pourrait modérer (et déclencher le
     * recalcul de note) l'avis d'un AUTRE restaurant.
     */
    @PostMapping("/shop-product-reviews-status")
    public Map<String, Object> shopProductReviewsStatus(@AuthenticationPrincipal String email,
                                                          @RequestBody Map<String, Object> body) {
        Restaurant restaurant = sellerContext.currentRestaurant(email); // 403 non-vendeur / 404 sans restaurant
        Long id = toLong(body.get("id"));
        requireOwnedAvis(restaurant, id); // 404 si l'avis n'appartient pas au vendeur
        int status = toLong(body.get("status")) != null ? toLong(body.get("status")).intValue() : 0;
        ModerationAvisDTO dto = new ModerationAvisDTO();
        if (status == 1) {
            dto.setStatut(StatutAvis.APPROUVE);
        } else {
            dto.setStatut(StatutAvis.REJETE);
            dto.setRaisonRejet("Rejeté depuis l'app vendeur.");
        }
        avisService.moderAvis(id, dto);
        return mapper.success("Statut de l'avis mis à jour.");
    }

    /**
     * Charge l'avis par id et vérifie qu'il appartient au restaurant du vendeur authentifié.
     * Sinon 404 (jamais de fuite/mutation cross-vendeur) — miroir de {@code ownedOrder}/
     * {@code ownedPlat}/{@code requireOwnedCoupon} des tranches précédentes.
     */
    private void requireOwnedAvis(Restaurant restaurant, Long id) {
        AvisDTO avis;
        try {
            avis = id != null ? avisService.getAvisById(id) : null;
        } catch (ResourceNotFoundException e) {
            avis = null;
        }
        if (avis == null || avis.getRestaurantId() == null
                || !avis.getRestaurantId().equals(restaurant.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avis non trouvé");
        }
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
