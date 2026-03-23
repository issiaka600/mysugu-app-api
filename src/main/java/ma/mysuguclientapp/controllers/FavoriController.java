package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.FavoriDTO;
import ma.mysuguclientapp.services.interfaces.FavoriService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favoris")
@RequiredArgsConstructor
public class FavoriController {

    private final FavoriService favoriService;

    @GetMapping
    public ResponseEntity<List<FavoriDTO>> getMesFavoris(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(favoriService.getMesFavoris(token));
    }

    @PostMapping("/{restaurantId}")
    public ResponseEntity<FavoriDTO> ajouterFavori(
            @RequestHeader("Authorization") String token,
            @PathVariable Long restaurantId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(favoriService.ajouterFavori(token, restaurantId));
    }

    @DeleteMapping("/{restaurantId}")
    public ResponseEntity<Void> supprimerFavori(
            @RequestHeader("Authorization") String token,
            @PathVariable Long restaurantId) {
        favoriService.supprimerFavori(token, restaurantId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{restaurantId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleFavori(
            @RequestHeader("Authorization") String token,
            @PathVariable Long restaurantId) {
        FavoriDTO result = favoriService.toggleFavori(token, restaurantId);
        boolean added = result != null;
        return ResponseEntity.ok(Map.of(
                "isFavori", added,
                "action", added ? "AJOUTE" : "SUPPRIME"
        ));
    }

    @GetMapping("/{restaurantId}/status")
    public ResponseEntity<Map<String, Boolean>> estFavori(
            @RequestHeader("Authorization") String token,
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(Map.of("isFavori", favoriService.estFavori(token, restaurantId)));
    }

    @GetMapping("/{restaurantId}/count")
    public ResponseEntity<Map<String, Long>> getNombreFavoris(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(Map.of("count", favoriService.getNombreFavoris(restaurantId)));
    }
}
