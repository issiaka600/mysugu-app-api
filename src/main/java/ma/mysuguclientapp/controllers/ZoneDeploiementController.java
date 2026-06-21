package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.ZoneAttenteNotificationCreateDTO;
import ma.mysuguclientapp.dtos.ZoneAttenteNotificationDTO;
import ma.mysuguclientapp.dtos.ZoneDeploiementDTO;
import ma.mysuguclientapp.services.interfaces.ZoneAttenteNotificationService;
import ma.mysuguclientapp.services.interfaces.ZoneDeploiementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestion des zones de déploiement MySugu.
 *
 * - Vue publique (GET actives) : utilisée par l'app mobile pour afficher
 *   les villes couvertes et pré-remplir le sélecteur lors de la création
 *   d'un restaurant.
 * - Vue admin (GET all, POST, PUT, PATCH, DELETE) : réservée à l'admin.
 */
@RestController
@RequestMapping("/api/zones-deploiement")
@RequiredArgsConstructor
public class ZoneDeploiementController {

    private final ZoneDeploiementService zoneDeploiementService;
    private final ZoneAttenteNotificationService zoneAttenteNotificationService;

    /** Public : enregistre une demande lorsqu'un client est hors-zone. */
    @PostMapping("/notification")
    public ResponseEntity<ZoneAttenteNotificationDTO> enregistrerDemandeHorsZone(
            @RequestBody ZoneAttenteNotificationCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(zoneAttenteNotificationService.enregistrer(dto));
    }

    /** Zones actives — accessible sans authentification (sélecteur côté client/restaurant). */
    @GetMapping("/actives")
    public ResponseEntity<List<ZoneDeploiementDTO>> getZonesActives() {
        return ResponseEntity.ok(zoneDeploiementService.getZonesActives());
    }

    /** Toutes les zones (actives + inactives) — admin. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ZoneDeploiementDTO>> getAllZones() {
        return ResponseEntity.ok(zoneDeploiementService.getAllZones());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ZoneDeploiementDTO> getZone(@PathVariable Long id) {
        return ResponseEntity.ok(zoneDeploiementService.getZone(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ZoneDeploiementDTO> creerZone(@RequestBody ZoneDeploiementDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneDeploiementService.creerZone(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ZoneDeploiementDTO> modifierZone(@PathVariable Long id,
                                                            @RequestBody ZoneDeploiementDTO dto) {
        return ResponseEntity.ok(zoneDeploiementService.modifierZone(id, dto));
    }

    @PatchMapping("/{id}/activer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ZoneDeploiementDTO> toggleZone(@PathVariable Long id,
                                                          @RequestParam boolean actif) {
        return ResponseEntity.ok(zoneDeploiementService.toggleZone(id, actif));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> supprimerZone(@PathVariable Long id) {
        zoneDeploiementService.supprimerZone(id);
        return ResponseEntity.noContent().build();
    }
}
