package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.commerce.PromotionCreateDTO;
import ma.mysuguclientapp.dtos.commerce.PromotionDTO;
import ma.mysuguclientapp.services.interfaces.PromotionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PromotionDTO> creerPromotion(@RequestBody PromotionCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promotionService.creerPromotion(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromotionDTO> getPromotion(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.getPromotion(id));
    }

    @GetMapping
    public ResponseEntity<List<PromotionDTO>> getPromotionsActives() {
        return ResponseEntity.ok(promotionService.getPromotionsActives());
    }

    @GetMapping("/flash")
    public ResponseEntity<List<PromotionDTO>> getPromotionsFlash() {
        return ResponseEntity.ok(promotionService.getPromotionsFlash());
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<PromotionDTO>> getPromotionsRestaurant(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(promotionService.getPromotionsRestaurant(restaurantId));
    }

    @PatchMapping("/{id}/activer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PromotionDTO> activerDesactiver(@PathVariable Long id,
                                                           @RequestParam boolean actif) {
        return ResponseEntity.ok(promotionService.activerDesactiver(id, actif));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> supprimerPromotion(@PathVariable Long id) {
        promotionService.supprimerPromotion(id);
        return ResponseEntity.noContent().build();
    }
}
