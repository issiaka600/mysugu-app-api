package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.restaurant.ZoneLivraisonDTO;
import ma.mysuguclientapp.services.implementations.ZoneLivraisonServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/zones-livraison")
@RequiredArgsConstructor
public class ZoneLivraisonController {

    private final ZoneLivraisonServiceImpl zoneLivraisonService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public ResponseEntity<ZoneLivraisonDTO> creerZone(@RequestBody ZoneLivraisonDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneLivraisonService.creerZone(dto));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<ZoneLivraisonDTO>> getZones(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(zoneLivraisonService.getZonesRestaurant(restaurantId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public ResponseEntity<ZoneLivraisonDTO> modifierZone(@PathVariable Long id, @RequestBody ZoneLivraisonDTO dto) {
        return ResponseEntity.ok(zoneLivraisonService.modifierZone(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public ResponseEntity<Void> supprimerZone(@PathVariable Long id) {
        zoneLivraisonService.supprimerZone(id);
        return ResponseEntity.noContent().build();
    }
}
