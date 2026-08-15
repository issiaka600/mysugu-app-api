package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.EnumOptionDTO;
import ma.mysuguclientapp.services.interfaces.CategorieProduitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/plats")
@RequiredArgsConstructor
public class CategorieProduitController {

    private final CategorieProduitService categorieProduitService;

    @GetMapping("/categories-produit")
    public ResponseEntity<List<EnumOptionDTO>> getCategoriesProduit(@RequestParam String vertical) {
        return ResponseEntity.ok(categorieProduitService.listerPublic(vertical));
    }
}
