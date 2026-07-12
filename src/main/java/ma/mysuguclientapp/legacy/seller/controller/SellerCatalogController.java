package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.CategoriePlat;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.ProductSellerMapper;
import ma.mysuguclientapp.services.interfaces.PlatService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Catalogue partagé du shim vendeur : {@code brands} (aucun équivalent natif — STUB),
 * {@code categories} (mappé depuis {@link CategoriePlat}), {@code products/barcode/generate} et
 * {@code products/upload-digital-product} (6valley-ismes sans équivalent MySugu — STUB bénin).
 * Voir docs/superpowers/specs/2026-07-10-vendor-3c-products-design.md §3/§6.
 */
@RestController
@RequestMapping("/api/v3/seller")
@RequiredArgsConstructor
public class SellerCatalogController {

    private final SellerContext sellerContext;
    private final PlatService platService;
    private final ProductSellerMapper mapper;

    /** GET brands : aucun modèle marque natif -> liste vide. // STUB (umbrella §4 GAP). */
    @GetMapping("/brands")
    public List<Object> brands(@AuthenticationPrincipal String email) {
        sellerContext.currentRestaurant(email); // 403 non-vendeur / 404 sans restaurant
        return List.of();
    }

    /** GET categories : CategoriePlat -> forme 6valley plate (écran ajout produit). */
    @GetMapping("/categories")
    public List<Map<String, Object>> categories(@AuthenticationPrincipal String email) {
        sellerContext.currentRestaurant(email); // 403 non-vendeur / 404 sans restaurant
        return List.of(CategoriePlat.values()).stream().map(mapper::category).toList();
    }

    /**
     * POST products/barcode/generate {id} : aucun modèle code-barres natif -> code dérivé
     * bénin de l'id du produit, pour que l'écran d'impression s'affiche. Appartenance vérifiée.
     * // STUB: no native equivalent (umbrella §4 GAP).
     */
    @PostMapping("/products/barcode/generate")
    public Map<String, Object> barcodeGenerate(@AuthenticationPrincipal String email,
                                                @RequestBody Map<String, Object> body) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        Long id = toLong(body.get("id"));
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id requis");
        }
        PlatDTO plat = platService.getPlatById(id);
        if (plat.getRestaurantId() == null || !plat.getRestaurantId().equals(restaurant.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit non trouvé");
        }
        return Map.of("barcode", List.of("MSG-" + plat.getId()));
    }

    /**
     * POST products/upload-digital-product : aucun modèle produit numérique natif -> accusé
     * bénin, jamais 404/500. // STUB: no native equivalent (umbrella §4 GAP).
     */
    @PostMapping("/products/upload-digital-product")
    public Map<String, Object> uploadDigitalProduct(@AuthenticationPrincipal String email,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        sellerContext.currentRestaurant(email); // 403 non-vendeur / 404 sans restaurant
        return mapper.success("Produits numériques non pris en charge.");
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id invalide");
        }
    }
}
