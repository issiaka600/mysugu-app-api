package ma.mysuguclientapp.controllers;

import ma.mysuguclientapp.dtos.EnumOptionDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plats")
public class CategorieProduitController {

    private static final List<EnumOptionDTO> ALIMENTAIRE = List.of(
            new EnumOptionDTO("fruits_legumes", "Fruits & légumes"),
            new EnumOptionDTO("epicerie", "Épicerie"),
            new EnumOptionDTO("boissons", "Boissons"),
            new EnumOptionDTO("produits_frais", "Produits frais")
    );
    private static final List<EnumOptionDTO> COSMETIQUE = List.of(
            new EnumOptionDTO("soin_visage", "Soin visage"),
            new EnumOptionDTO("soin_corps", "Soin corps"),
            new EnumOptionDTO("parfums", "Parfums"),
            new EnumOptionDTO("cheveux", "Cheveux")
    );
    private static final Map<String, List<EnumOptionDTO>> BY_VERTICAL = Map.of(
            "ALIMENTAIRE", ALIMENTAIRE,
            "COSMETIQUE", COSMETIQUE
    );

    @GetMapping("/categories-produit")
    public ResponseEntity<List<EnumOptionDTO>> getCategoriesProduit(@RequestParam String vertical) {
        return ResponseEntity.ok(BY_VERTICAL.getOrDefault(vertical.toUpperCase(), List.of()));
    }
}
