package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CategorieProduitDTO;
import ma.mysuguclientapp.services.interfaces.CategorieProduitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gestion des rayons (catégories produit) par l'admin. Sécurisé par /api/admin/** → ADMIN. */
@RestController
@RequestMapping("/api/admin/categories-produit")
@RequiredArgsConstructor
public class AdminCategorieProduitController {

    private final CategorieProduitService categorieProduitService;

    @GetMapping
    public ResponseEntity<List<CategorieProduitDTO>> lister(@RequestParam String vertical) {
        return ResponseEntity.ok(categorieProduitService.listerAdmin(vertical));
    }

    @PostMapping
    public ResponseEntity<CategorieProduitDTO> creer(@RequestBody CategorieProduitDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categorieProduitService.creer(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategorieProduitDTO> modifier(@PathVariable Long id,
                                                        @RequestBody CategorieProduitDTO dto) {
        return ResponseEntity.ok(categorieProduitService.modifier(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        categorieProduitService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
