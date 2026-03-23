package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.caisse.DetteRestaurantDTO;
import ma.mysuguclientapp.dtos.caisse.PaiementRestaurantDTO;
import ma.mysuguclientapp.dtos.caisse.ParametresPaiementRestaurantDTO;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.FacturationRestaurantServiceImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/facturation-restaurant")
@RequiredArgsConstructor
public class FacturationRestaurantController {

    private final FacturationRestaurantServiceImpl facturationService;
    private final UserRepository userRepository;

    // =====================================================================
    // PARAMÈTRES — accessibles par le propriétaire du restaurant ET l'admin
    // =====================================================================

    /**
     * GET /api/facturation-restaurant/{restaurantId}/parametres
     */
    @GetMapping("/{restaurantId}/parametres")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<ParametresPaiementRestaurantDTO> getParametres(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(facturationService.getParametres(restaurantId));
    }

    /**
     * PUT /api/facturation-restaurant/{restaurantId}/parametres
     */
    @PutMapping("/{restaurantId}/parametres")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<ParametresPaiementRestaurantDTO> updateParametres(
            @PathVariable Long restaurantId,
            @RequestBody ParametresPaiementRestaurantDTO dto) {
        return ResponseEntity.ok(facturationService.updateParametres(restaurantId, dto));
    }

    // =====================================================================
    // DETTES — admin seulement
    // =====================================================================

    /**
     * GET /api/facturation-restaurant/{restaurantId}/dettes
     */
    @GetMapping("/{restaurantId}/dettes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DetteRestaurantDTO>> getDettes(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(facturationService.getDettesEnAttente(restaurantId));
    }

    /**
     * GET /api/facturation-restaurant/{restaurantId}/dettes/total
     */
    @GetMapping("/{restaurantId}/dettes/total")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BigDecimal> getTotalDette(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(facturationService.getTotalDetteEnAttente(restaurantId));
    }

    // =====================================================================
    // PAIEMENTS — admin seulement
    // =====================================================================

    /**
     * Déclencher un paiement groupé au restaurant.
     * POST /api/facturation-restaurant/{restaurantId}/payer
     */
    @PostMapping("/{restaurantId}/payer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaiementRestaurantDTO> payer(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime periodeDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime periodeFin,
            @RequestParam(required = false) String note,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long adminId = getUserId(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(facturationService.creerPaiementPeriodique(restaurantId, adminId, periodeDebut, periodeFin, note));
    }

    /**
     * Historique des paiements d'un restaurant.
     * GET /api/facturation-restaurant/{restaurantId}/paiements
     */
    @GetMapping("/{restaurantId}/paiements")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<Page<PaiementRestaurantDTO>> getHistoriquePaiements(
            @PathVariable Long restaurantId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(facturationService.getHistoriquePaiements(restaurantId, pageable));
    }

    /**
     * Détail des dettes d'un paiement.
     * GET /api/facturation-restaurant/paiements/{paiementId}/dettes
     */
    @GetMapping("/paiements/{paiementId}/dettes")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<List<DetteRestaurantDTO>> getDettesParPaiement(@PathVariable Long paiementId) {
        return ResponseEntity.ok(facturationService.getDettesParPaiement(paiementId));
    }

    // =====================================================================
    // HELPER
    // =====================================================================

    private Long getUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }
}
