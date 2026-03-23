package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.caisse.*;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.CaisseServiceImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/caisse")
@RequiredArgsConstructor
public class CaisseController {

    private final CaisseServiceImpl caisseService;
    private final UserRepository userRepository;

    // =====================================================================
    // LIVREUR — sa propre position de caisse
    // =====================================================================

    @GetMapping("/ma-position")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<CaisseLivreurDTO> maPosition(@AuthenticationPrincipal String email) {
        Long livreurId = getUserIdByEmail(email);
        return ResponseEntity.ok(caisseService.getPositionLivreur(livreurId));
    }

    @GetMapping("/mon-historique")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<Page<TransactionCaisseDTO>> monHistorique(
            @AuthenticationPrincipal String email,
            @PageableDefault(size = 20) Pageable pageable) {
        Long livreurId = getUserIdByEmail(email);
        return ResponseEntity.ok(caisseService.getHistoriqueCaisse(livreurId, pageable));
    }

    @GetMapping("/info-commande/{commandeId}")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<InfoPaiementCommandeDTO> infoCommande(
            @PathVariable Long commandeId,
            @AuthenticationPrincipal String email) {
        Long livreurId = getUserIdByEmail(email);
        return ResponseEntity.ok(caisseService.getInfoPaiementCommande(commandeId, livreurId));
    }

    @PostMapping("/paiement-restaurant/{commandeId}")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<CaisseLivreurDTO> confirmerPaiementRestaurant(
            @PathVariable Long commandeId,
            @AuthenticationPrincipal String email) {
        Long livreurId = getUserIdByEmail(email);
        return ResponseEntity.ok(caisseService.confirmerPaiementRestaurant(commandeId, livreurId));
    }

    // =====================================================================
    // ADMIN — gestion globale
    // =====================================================================

    @GetMapping("/bord-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BordCaisseAdminDTO> bordAdmin() {
        return ResponseEntity.ok(caisseService.getBordAdmin());
    }

    @GetMapping("/{livreurId}/position")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CaisseLivreurDTO> positionLivreur(@PathVariable Long livreurId) {
        return ResponseEntity.ok(caisseService.getPositionLivreur(livreurId));
    }

    @GetMapping("/{livreurId}/historique")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<TransactionCaisseDTO>> historiqueLivreur(
            @PathVariable Long livreurId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(caisseService.getHistoriqueCaisse(livreurId, pageable));
    }

    /**
     * Accorder une avance de liquidités au livreur.
     * POST /api/caisse/avance/{livreurId}
     * Body JSON: { "montant": 50.00, "note": "..." }
     */
    @PostMapping("/avance/{livreurId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CaisseLivreurDTO> accordAvance(
            @PathVariable Long livreurId,
            @RequestBody AvanceRequestDTO body,
            @AuthenticationPrincipal String adminEmail) {
        Long adminId = getUserIdByEmail(adminEmail);
        return ResponseEntity.ok(caisseService.accordAvanceLivreur(livreurId, body.getMontant(), adminId, body.getNote()));
    }

    /**
     * Réconciliation : le livreur vient remettre les espèces.
     * POST /api/caisse/reconcilier
     * Body JSON: { "livreurId": 3, "montantRemis": 100.00, "note": "..." }
     */
    @PostMapping("/reconcilier")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReconciliationResultDTO> reconcilier(
            @RequestBody ReconciliationDTO dto,
            @AuthenticationPrincipal String adminEmail) {
        Long adminId = getUserIdByEmail(adminEmail);
        return ResponseEntity.ok(caisseService.reconcilier(dto, adminId));
    }

    @GetMapping("/parametres")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ParametresCaisseDTO> getParametres() {
        return ResponseEntity.ok(caisseService.getParametres());
    }

    @PutMapping("/parametres")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ParametresCaisseDTO> updateParametres(@RequestBody ParametresCaisseDTO dto) {
        return ResponseEntity.ok(caisseService.updateParametres(dto));
    }

    @PutMapping("/{livreurId}/plafond")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CaisseLivreurDTO> setPlafond(
            @PathVariable Long livreurId,
            @RequestParam BigDecimal plafond) {
        return ResponseEntity.ok(caisseService.setPlafondPersonnaliseLivreur(livreurId, plafond));
    }

    // =====================================================================
    // HELPER
    // =====================================================================

    private Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }
}
