package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.EnumOptionDTO;
import ma.mysuguclientapp.services.interfaces.CategoriePlatDefService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catégories de plats côté public/client : listing des catégories actives, ordonnées depuis le dashboard. */
@RestController
@RequestMapping("/api/plats")
@RequiredArgsConstructor
public class CategoriePlatController {

    private final CategoriePlatDefService categoriePlatDefService;

    @GetMapping("/categories")
    public ResponseEntity<List<EnumOptionDTO>> getCategoriesPlat() {
        return ResponseEntity.ok(categoriePlatDefService.listerPublic());
    }
}