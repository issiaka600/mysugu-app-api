package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.PlatCreateDTO;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.services.interfaces.PlatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/plats")
@RequiredArgsConstructor
@Slf4j
public class PlatController {

    private final PlatService platService;

    /**
     * GET /api/plats - Obtenir tous les plats
     */
    @GetMapping
    public ResponseEntity<Page<PlatDTO>> getAllPlats(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) Boolean available,
            Pageable pageable) {
        
        Page<PlatDTO> plats = platService.getAllPlats(restaurantId, categorie, available, pageable);
        return ResponseEntity.ok(plats);
    }

    /**
     * GET /api/plats/{id} - Obtenir un plat par ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlatDTO> getPlatById(@PathVariable Long id) {
        PlatDTO plat = platService.getPlatById(id);
        return ResponseEntity.ok(plat);
    }

    /**
     * GET /api/plats/restaurant/{restaurantId} - Plats d'un restaurant
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<PlatDTO>> getPlatsByRestaurant(@PathVariable Long restaurantId) {
        List<PlatDTO> plats = platService.getPlatsByRestaurant(restaurantId);
        return ResponseEntity.ok(plats);
    }

    /**
     * GET /api/plats/search - Rechercher des plats
     */
    @GetMapping("/search")
    public ResponseEntity<List<PlatDTO>> searchPlats(@RequestParam String keyword) {
        List<PlatDTO> plats = platService.searchPlats(keyword);
        return ResponseEntity.ok(plats);
    }

    /**
     * POST /api/plats - Créer un nouveau plat
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlatDTO> createPlat(
            @Valid @ModelAttribute PlatCreateDTO platDTO,
            @RequestParam(required = false) MultipartFile image) {
        
        PlatDTO created = platService.createPlat(platDTO, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/plats/{id} - Mettre à jour un plat
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlatDTO> updatePlat(
            @PathVariable Long id,
            @Valid @ModelAttribute PlatCreateDTO platDTO,
            @RequestParam(required = false) MultipartFile image) {
        log.info("Plat Update Object : {}", platDTO);
        PlatDTO updated = platService.updatePlat(id, platDTO, image);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/plats/{id} - Supprimer un plat
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlat(@PathVariable Long id) {
        platService.deletePlat(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/plats/{id}/availability - Changer la disponibilité
     */
    @PatchMapping("/{id}/availability")
    public ResponseEntity<PlatDTO> togglePlatAvailability(@PathVariable Long id) {
        PlatDTO updated = platService.toggleAvailability(id);
        return ResponseEntity.ok(updated);
    }
}
