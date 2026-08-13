package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CategorieRestaurantDTO;
import ma.mysuguclientapp.services.interfaces.CategorieRestaurantService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategorieRestaurantController {

    private final CategorieRestaurantService categorieService;

    /**
     * GET /api/categories - Obtenir toutes les catégories
     * @param vertical filtre optionnel : absent ⇒ RESTAURANT, "ALL" ⇒ toutes verticales
     */
    @GetMapping
    public ResponseEntity<List<CategorieRestaurantDTO>> getAllCategories(
            @RequestParam(required = false) String vertical) {
        List<CategorieRestaurantDTO> categories = categorieService.getAllCategories(vertical);
        return ResponseEntity.ok(categories);
    }

    /**
     * GET /api/categories/{id} - Obtenir une catégorie par ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<CategorieRestaurantDTO> getCategorieById(@PathVariable Long id) {
        CategorieRestaurantDTO categorie = categorieService.getCategorieById(id);
        return ResponseEntity.ok(categorie);
    }

    /**
     * POST /api/categories - Créer une nouvelle catégorie
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CategorieRestaurantDTO> createCategorie(
            @Valid @RequestParam String nom,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile imageTop,
            @RequestParam(required = false) MultipartFile imageBanner) {
        
        CategorieRestaurantDTO created = categorieService.createCategorie(
                nom,
                description,
                image,
                imageTop,
                imageBanner);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/categories/{id} - Mettre à jour une catégorie
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CategorieRestaurantDTO> updateCategorie(
            @PathVariable Long id,
            @RequestParam String nom,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile imageTop,
            @RequestParam(required = false) MultipartFile imageBanner) {
        
        CategorieRestaurantDTO updated = categorieService.updateCategorie(
                id,
                nom,
                description,
                image,
                imageTop,
                imageBanner);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/categories/{id} - Supprimer une catégorie
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategorie(@PathVariable Long id) {
        categorieService.deleteCategorie(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/categories/{id}/restaurants - Restaurants d'une catégorie
     */
    @GetMapping("/{id}/restaurants")
    public ResponseEntity<List<?>> getRestaurantsByCategorie(@PathVariable Long id) {
        return ResponseEntity.ok(categorieService.getRestaurantsByCategorie(id));
    }
}
