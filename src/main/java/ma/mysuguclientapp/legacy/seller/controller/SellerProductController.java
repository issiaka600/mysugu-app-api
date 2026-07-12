package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.ProductSellerMapper;
import ma.mysuguclientapp.services.interfaces.PlatService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Produits du shim vendeur (contrat 6valley) : {@code /api/v3/seller/products/*} réutilise
 * {@link PlatService} en scopant systématiquement au restaurant du vendeur authentifié via
 * {@link SellerContext#currentRestaurant}. Un {@code Plat} appartenant à un AUTRE restaurant
 * -> 404 (jamais de fuite/mutation cross-restaurant) : c'est la garde de sécurité critique de
 * cette tranche, voir docs/superpowers/specs/2026-07-10-vendor-3c-products-design.md §5.
 */
@RestController
@RequestMapping("/api/v3/seller/products")
@RequiredArgsConstructor
@Slf4j
public class SellerProductController {

    private final SellerContext sellerContext;
    private final PlatService platService;
    private final ProductSellerMapper mapper;

    /** GET products/?limit&offset : produits du restaurant du vendeur, forme 6valley paginée. */
    @GetMapping({"", "/"})
    public Map<String, Object> list(@AuthenticationPrincipal String email,
                                     @RequestParam(defaultValue = "10") int limit,
                                     @RequestParam(defaultValue = "0") int offset) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        Page<PlatDTO> page = platService.getAllPlats(restaurant.getId(), null, null,
                PageRequest.of(offset / Math.max(limit, 1), Math.max(limit, 1)));
        return mapper.listEnvelope("products", page);
    }

    /** GET products/details/{id} et GET products/edit/{id} : détail d'un produit du vendeur. */
    @GetMapping({"/details/{id}", "/edit/{id}"})
    public Map<String, Object> detail(@AuthenticationPrincipal String email, @PathVariable Long id) {
        PlatDTO plat = ownedPlat(email, id);
        return mapper.toSixValley(plat);
    }

    /**
     * Résout le Plat par id et vérifie qu'il appartient au restaurant du vendeur authentifié.
     * Sinon 404 (jamais de fuite cross-restaurant) — garde réutilisée par tous les endpoints
     * produits (détail, écriture, statut, images).
     */
    private PlatDTO ownedPlat(String email, Long id) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        PlatDTO plat = platService.getPlatById(id);
        if (plat.getRestaurantId() == null || !plat.getRestaurantId().equals(restaurant.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit non trouvé");
        }
        return plat;
    }
}
