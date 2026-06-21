package ma.mysuguclientapp.controllers;

import ma.mysuguclientapp.dtos.EnumOptionDTO;
import ma.mysuguclientapp.enumerations.CategoriePlat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plats")
public class CategoriePlatController {

    private static final Map<CategoriePlat, String> LABELS = Map.of(
            CategoriePlat.ENTREE, "Entrée",
            CategoriePlat.PLAT_PRINCIPAL, "Plat principal",
            CategoriePlat.DESSERT, "Dessert",
            CategoriePlat.BOISSON, "Boisson",
            CategoriePlat.ACCOMPAGNEMENT, "Accompagnement"
    );

    @GetMapping("/categories")
    public ResponseEntity<List<EnumOptionDTO>> getCategoriesPlat() {
        List<EnumOptionDTO> options = List.of(CategoriePlat.values()).stream()
                .map(c -> new EnumOptionDTO(c.name(), LABELS.getOrDefault(c, c.name())))
                .toList();
        return ResponseEntity.ok(options);
    }
}
