package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.dtos.RestaurantCreateDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import ma.mysuguclientapp.services.interfaces.PlatService;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final PlatService platService;

    /**
     * GET /api/restaurants - Obtenir tous les restaurants (avec pagination)
     */
    @GetMapping
    public ResponseEntity<Page<RestaurantDTO>> getAllRestaurants(
            @RequestParam(required = false) Long categorieId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double maxDistance, // en km
            @RequestParam(required = false) String vertical,
            Pageable pageable) {

        Page<RestaurantDTO> restaurants = restaurantService.getAllRestaurants(
            categorieId, latitude, longitude, maxDistance, vertical, pageable);
        return ResponseEntity.ok(restaurants);
    }

    /**
     * GET /api/restaurants/{id} - Obtenir un restaurant par ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<RestaurantDTO> getRestaurantById(@PathVariable Long id) {
        RestaurantDTO restaurant = restaurantService.getRestaurantById(id);
        return ResponseEntity.ok(restaurant);
    }

    /**
     * GET /api/restaurants/search - Rechercher des restaurants
     */
    @GetMapping("/search")
    public ResponseEntity<List<RestaurantDTO>> searchRestaurants(
            @RequestParam String keyword,
            @RequestParam(required = false) String vertical) {
        List<RestaurantDTO> restaurants = restaurantService.searchRestaurants(keyword, vertical);
        return ResponseEntity.ok(restaurants);
    }

    /**
     * GET /api/restaurants/top-rated - Restaurants les mieux notés
     */
    @GetMapping("/top-rated")
    public ResponseEntity<List<RestaurantDTO>> getTopRatedRestaurants(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String vertical) {
        List<RestaurantDTO> restaurants = restaurantService.getTopRatedRestaurants(limit, vertical);
        return ResponseEntity.ok(restaurants);
    }

    /**
     * GET /api/restaurants/nearby - Restaurants à proximité
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<RestaurantDTO>> getNearbyRestaurants(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "5.0") Double radiusKm,
            @RequestParam(required = false) String vertical) {
        List<RestaurantDTO> restaurants = restaurantService.getNearbyRestaurants(latitude, longitude, radiusKm, vertical);
        return ResponseEntity.ok(restaurants);
    }

    /**
     * POST /api/restaurants - Créer un nouveau restaurant
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RestaurantDTO> createRestaurant(
            @Valid @ModelAttribute RestaurantCreateDTO restaurantDTO,
            @RequestParam(value = "logo", required = false) MultipartFile logo) {

        RestaurantDTO created = restaurantService.createRestaurant(restaurantDTO, logo);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/restaurants/{id} - Mettre à jour un restaurant
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RestaurantDTO> updateRestaurant(
            @PathVariable Long id,
            @Valid @ModelAttribute RestaurantCreateDTO restaurantDTO,
            @RequestParam(value = "logo", required = false) MultipartFile logo) {
        
        RestaurantDTO updated = restaurantService.updateRestaurant(id, restaurantDTO, logo);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/restaurants/{id} - Supprimer un restaurant
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRestaurant(@PathVariable Long id) {
        restaurantService.deleteRestaurant(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/restaurants/{id}/activate - Activer/désactiver un restaurant
     */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<RestaurantDTO> toggleRestaurantStatus(@PathVariable Long id) {
        RestaurantDTO updated = restaurantService.toggleRestaurantStatus(id);
        return ResponseEntity.ok(updated);
    }

    /**
     * PATCH /api/restaurants/{id}/commission - Définir le taux de commission d'un restaurant (admin)
     */
    @PatchMapping("/{id}/commission")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestaurantDTO> setCommission(
            @PathVariable Long id,
            @RequestParam java.math.BigDecimal pourcentage) {
        RestaurantDTO updated = restaurantService.setCommissionPourcentage(id, pourcentage);
        return ResponseEntity.ok(updated);
    }

    /**
     * GET /api/restaurants/{id}/plats - Obtenir les plats d'un restaurant
     */
    @GetMapping("/{id}/plats")
    public ResponseEntity<List<PlatDTO>> getRestaurantPlats(@PathVariable Long id) {
        return ResponseEntity.ok(platService.getPlatsByRestaurant(id));
    }
}
