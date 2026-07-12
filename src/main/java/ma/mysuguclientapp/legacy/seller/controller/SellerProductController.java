package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.PlatAvailabilityUpdateDTO;
import ma.mysuguclientapp.dtos.PlatCreateDTO;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.ModeDisponibilitePlat;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.ProductSellerMapper;
import ma.mysuguclientapp.services.interfaces.PlatService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
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
     * POST products/add (multipart) : crée un produit sous le restaurant du vendeur
     * authentifié. {@code restaurantId} est TOUJOURS forcé côté serveur (jamais depuis le
     * corps) — pas de création cross-tenant. Champs 6valley sans équivalent natif (tax, sku,
     * brand_id, meta_*, colors) acceptés implicitement et ignorés (non liés).
     */
    @PostMapping(value = "/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> add(@AuthenticationPrincipal String email,
                                    @RequestParam(value = "name", required = false) String name,
                                    @RequestParam(value = "details", required = false) String details,
                                    @RequestParam(value = "price", required = false) BigDecimal price,
                                    @RequestParam(value = "unit_price", required = false) BigDecimal unitPrice,
                                    @RequestParam(value = "category_id", required = false) Long categoryId,
                                    @RequestParam(value = "image", required = false) MultipartFile image) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        PlatCreateDTO dto = toPlatCreateDTO(restaurant.getId(), name, details, price, unitPrice, categoryId);
        platService.createPlat(dto, image);
        return mapper.success("Produit ajouté.");
    }

    /**
     * POST products/update (multipart, {@code _method:put} toléré) : met à jour un produit du
     * vendeur authentifié. L'id est lu du formulaire mais l'appartenance est TOUJOURS vérifiée
     * via {@link #ownedPlat} avant écriture — un Plat d'un AUTRE restaurant -> 404 (jamais de
     * mutation cross-tenant).
     */
    @PostMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> update(@AuthenticationPrincipal String email,
                                       @RequestParam(value = "id") Long id,
                                       @RequestParam(value = "name", required = false) String name,
                                       @RequestParam(value = "details", required = false) String details,
                                       @RequestParam(value = "price", required = false) BigDecimal price,
                                       @RequestParam(value = "unit_price", required = false) BigDecimal unitPrice,
                                       @RequestParam(value = "category_id", required = false) Long categoryId,
                                       @RequestParam(value = "image", required = false) MultipartFile image) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        ownedPlat(email, id); // 404 si le produit n'appartient pas au vendeur
        PlatCreateDTO dto = toPlatCreateDTO(restaurant.getId(), name, details, price, unitPrice, categoryId);
        platService.updatePlat(id, dto, image);
        return mapper.success("Produit mis à jour.");
    }

    /**
     * POST products/delete {id} : supprime un produit du vendeur authentifié. Appartenance
     * TOUJOURS vérifiée avant suppression — un Plat d'un AUTRE restaurant -> 404 (jamais de
     * suppression cross-tenant).
     */
    @PostMapping("/delete")
    public Map<String, Object> delete(@AuthenticationPrincipal String email,
                                       @RequestBody Map<String, Object> body) {
        Long id = toLong(body.get("id"));
        ownedPlat(email, id); // 404 si le produit n'appartient pas au vendeur
        platService.deletePlat(id);
        return mapper.success("Produit supprimé.");
    }

    /**
     * POST products/status-update {id,status} : bascule Plat.isAvailable via
     * PlatService.updateAvailability. {@code status=1} => DISPONIBLE, {@code status=0} =>
     * INDISPONIBLE_DEFINITIVE (pas de créneau temporaire côté 6valley). Appartenance TOUJOURS
     * vérifiée avant écriture — un Plat d'un AUTRE restaurant -> 404.
     */
    @PostMapping("/status-update")
    public Map<String, Object> statusUpdate(@AuthenticationPrincipal String email,
                                             @RequestBody Map<String, Object> body) {
        Long id = toLong(body.get("id"));
        ownedPlat(email, id); // 404 si le produit n'appartient pas au vendeur
        int status = toLong(body.get("status")) != null ? toLong(body.get("status")).intValue() : 0;
        PlatAvailabilityUpdateDTO dto = new PlatAvailabilityUpdateDTO();
        dto.setAvailabilityMode(status == 1
                ? ModeDisponibilitePlat.DISPONIBLE.name()
                : ModeDisponibilitePlat.INDISPONIBLE_DEFINITIVE.name());
        platService.updateAvailability(id, dto);
        return mapper.success("Statut du produit mis à jour.");
    }

    /**
     * POST products/upload-images (multipart) : {@code Plat} est mono-image (imageUrl) — pas de
     * galerie native. Mappé sur la même sémantique que {@code PATCH /api/plats/{id}/image}
     * (un seul fichier remplace l'image existante). Fichiers additionnels ignorés.
     * // GAP: Plat mono-image; gallery = follow-up (umbrella §7.2).
     */
    @PostMapping(value = "/upload-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadImages(@AuthenticationPrincipal String email,
                                             @RequestParam("id") Long id,
                                             @RequestParam(value = "image", required = false) MultipartFile image) {
        PlatDTO existing = ownedPlat(email, id); // 404 si le produit n'appartient pas au vendeur
        // NB: platService.updatePlat() écrase nom/description/prix avec les valeurs du DTO passé
        // (même comportement que le PATCH natif /api/plats/{id}/image) : on reporte donc les
        // champs existants pour ne pas les nuller.
        PlatDTO updated = platService.updatePlat(id, preserveFields(existing), image);
        String url = updated.getImageUrl();
        return Map.of("image", url != null ? List.of(url) : List.of());
    }

    /**
     * POST products/delete-image {id} : efface l'image (mono) du produit du vendeur.
     * // GAP: Plat mono-image; gallery = follow-up (umbrella §7.2).
     */
    @PostMapping("/delete-image")
    public Map<String, Object> deleteImage(@AuthenticationPrincipal String email,
                                            @RequestBody Map<String, Object> body) {
        Long id = toLong(body.get("id"));
        PlatDTO existing = ownedPlat(email, id); // 404 si le produit n'appartient pas au vendeur
        PlatCreateDTO dto = preserveFields(existing);
        dto.setRemoveImage(true);
        platService.updatePlat(id, dto, null);
        return mapper.success("Image supprimée.");
    }

    /** Reporte les champs existants d'un PlatDTO dans un PlatCreateDTO (évite de les nuller). */
    private PlatCreateDTO preserveFields(PlatDTO existing) {
        PlatCreateDTO dto = new PlatCreateDTO();
        dto.setNom(existing.getNom());
        dto.setDescription(existing.getDescription());
        dto.setPrix(existing.getPrix());
        dto.setIngredients(existing.getIngredients());
        dto.setCategoriePlat(existing.getCategoriePlat());
        dto.setCategorieProduit(existing.getCategorieProduit());
        dto.setTempsPreparation(existing.getTempsPreparation());
        return dto;
    }

    /**
     * GET products/get-product-images/{id} : image (mono) du produit, sous forme de liste
     * 6valley — {@code [<url>]} si présente, {@code []} sinon. // GAP: mono-image (umbrella §7.2).
     */
    @GetMapping("/get-product-images/{id}")
    public List<String> getProductImages(@AuthenticationPrincipal String email, @PathVariable Long id) {
        PlatDTO plat = ownedPlat(email, id); // 404 si le produit n'appartient pas au vendeur
        return plat.getImageUrl() != null ? List.of(plat.getImageUrl()) : List.of();
    }

    private PlatCreateDTO toPlatCreateDTO(Long restaurantId, String name, String details,
                                          BigDecimal price, BigDecimal unitPrice, Long categoryId) {
        PlatCreateDTO dto = new PlatCreateDTO();
        dto.setRestaurantId(restaurantId); // toujours forcé côté serveur, jamais depuis le corps
        dto.setNom(name);
        dto.setDescription(details);
        dto.setPrix(price != null ? price : unitPrice);
        if (categoryId != null) {
            ma.mysuguclientapp.enumerations.CategoriePlat c = mapper.categoryFromId(categoryId);
            if (c != null) {
                dto.setCategoriePlat(c.name());
            }
        }
        return dto;
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
