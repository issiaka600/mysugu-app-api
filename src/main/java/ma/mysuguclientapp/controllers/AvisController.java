package ma.mysuguclientapp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.AvisCreateDTO;
import ma.mysuguclientapp.dtos.AvisDTO;
import ma.mysuguclientapp.dtos.ModerationAvisDTO;
import ma.mysuguclientapp.services.interfaces.AvisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/avis")
@RequiredArgsConstructor
public class AvisController {

    private final AvisService avisService;

    @PostMapping
    public ResponseEntity<AvisDTO> soumettreAvis(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody AvisCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(avisService.soumettreAvis(token, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AvisDTO> getAvis(@PathVariable Long id) {
        return ResponseEntity.ok(avisService.getAvisById(id));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<AvisDTO>> getAvisRestaurant(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(avisService.getAvisRestaurant(restaurantId));
    }

    @GetMapping("/livreur/{livreurId}")
    public ResponseEntity<List<AvisDTO>> getAvisLivreur(@PathVariable Long livreurId) {
        return ResponseEntity.ok(avisService.getAvisLivreur(livreurId));
    }

    @GetMapping("/mes-avis")
    public ResponseEntity<List<AvisDTO>> getMesAvis(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(avisService.getMesAvis(token));
    }

    @PatchMapping("/{id}/moderation")
    public ResponseEntity<AvisDTO> moderAvis(
            @PathVariable Long id,
            @Valid @RequestBody ModerationAvisDTO dto) {
        return ResponseEntity.ok(avisService.moderAvis(id, dto));
    }

    @GetMapping("/admin/en-attente")
    public ResponseEntity<List<AvisDTO>> getAvisEnAttente() {
        return ResponseEntity.ok(avisService.getAvisEnAttente());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimerAvis(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        avisService.supprimerAvis(id, token);
        return ResponseEntity.noContent().build();
    }
}
