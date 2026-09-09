package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.TopVenteConfigDTO;
import ma.mysuguclientapp.dtos.TopVentePlatDTO;
import ma.mysuguclientapp.services.interfaces.TopVenteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Configuration et suivi de la rubrique « Top des ventes ».
 * Routes administrateur (ADMIN) — le détail plats/ventes ne doit pas fuiter en lecture publique.
 */
@RestController
@RequestMapping("/api/admin/top-vente")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TopVenteController {

    private final TopVenteService topVenteService;

    @GetMapping
    public ResponseEntity<TopVenteConfigDTO> getConfig() {
        return ResponseEntity.ok(topVenteService.getConfig());
    }

    @PutMapping
    public ResponseEntity<TopVenteConfigDTO> updateConfig(@RequestBody TopVenteConfigDTO dto) {
        return ResponseEntity.ok(topVenteService.updateConfig(dto));
    }

    /** Tableau des plats avec ventes cumulées, prêt pour la page « Top des ventes ». */
    @GetMapping("/plats")
    public ResponseEntity<Page<TopVentePlatDTO>> getPlatsTopVentes(
            @RequestParam(required = false) Boolean topVente,
            Pageable pageable) {
        return ResponseEntity.ok(topVenteService.getPlatsTopVentes(pageable, topVente));
    }
}