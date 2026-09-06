package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CategoriePlatDefDTO;
import ma.mysuguclientapp.services.interfaces.CategoriePlatDefService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gestion des catégories de plats par l'admin. Sécurisé par /api/admin/** → ADMIN. */
@RestController
@RequestMapping("/api/admin/plats-categories")
@RequiredArgsConstructor
public class AdminCategoriePlatController {

    private final CategoriePlatDefService categoriePlatDefService;

    @GetMapping
    public ResponseEntity<List<CategoriePlatDefDTO>> lister() {
        return ResponseEntity.ok(categoriePlatDefService.listerAdmin());
    }

    @PostMapping
    public ResponseEntity<CategoriePlatDefDTO> creer(@RequestBody CategoriePlatDefDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriePlatDefService.creer(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriePlatDefDTO> modifier(@PathVariable Long id,
                                                        @RequestBody CategoriePlatDefDTO dto) {
        return ResponseEntity.ok(categoriePlatDefService.modifier(id, dto));
    }

    @PatchMapping("/{id}/actif")
    public ResponseEntity<CategoriePlatDefDTO> activerDesactiver(@PathVariable Long id,
                                                                 @RequestParam boolean actif) {
        return ResponseEntity.ok(categoriePlatDefService.activerDesactiver(id, actif));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        categoriePlatDefService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}