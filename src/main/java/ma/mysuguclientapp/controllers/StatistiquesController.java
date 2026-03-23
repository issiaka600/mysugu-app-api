package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.stats.*;
import ma.mysuguclientapp.services.interfaces.StatistiquesService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/statistiques")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StatistiquesController {

    private final StatistiquesService statistiquesService;

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardOverviewDTO> getDashboard() {
        return ResponseEntity.ok(statistiquesService.getDashboardOverview());
    }

    // ==================== COMMANDES ====================

    @GetMapping("/commandes/evolution")
    public ResponseEntity<List<EvolutionCommandesDTO>> getEvolutionCommandes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(defaultValue = "JOUR") String periode) {

        LocalDate dateDebut = debut != null ? debut : LocalDate.now().withDayOfMonth(1);
        LocalDate dateFin = fin != null ? fin : LocalDate.now();

        if ("MOIS".equalsIgnoreCase(periode)) {
            int annee = dateDebut.getYear();
            return ResponseEntity.ok(statistiquesService.getEvolutionCommandesParMois(annee));
        }
        return ResponseEntity.ok(statistiquesService.getEvolutionCommandesParJour(dateDebut, dateFin));
    }

    @GetMapping("/commandes/par-statut")
    public ResponseEntity<List<CommandesParStatutDTO>> getCommandesParStatut(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getCommandesParStatut(
                debut != null ? debut : LocalDate.now().withDayOfMonth(1),
                fin != null ? fin : LocalDate.now()));
    }

    @GetMapping("/commandes/par-mode")
    public ResponseEntity<List<CommandesParModeDTO>> getCommandesParMode(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getCommandesParMode(
                debut != null ? debut : LocalDate.now().withDayOfMonth(1),
                fin != null ? fin : LocalDate.now()));
    }

    @GetMapping("/commandes/par-paiement")
    public ResponseEntity<List<CommandesParPaiementDTO>> getCommandesParPaiement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getCommandesParMethodePaiement(
                debut != null ? debut : LocalDate.now().withDayOfMonth(1),
                fin != null ? fin : LocalDate.now()));
    }

    @GetMapping("/commandes/heures-pointe")
    public ResponseEntity<List<HeurePointe>> getHeuresPointe(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getHeuresPointe(
                debut != null ? debut : LocalDate.now().minusDays(30),
                fin != null ? fin : LocalDate.now()));
    }

    // ==================== RESTAURANTS ====================

    @GetMapping("/restaurants/top")
    public ResponseEntity<List<TopRestaurantDTO>> getTopRestaurants(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(defaultValue = "COMMANDES") String tri) {

        LocalDate dateDebut = debut != null ? debut : LocalDate.now().withDayOfMonth(1);
        LocalDate dateFin = fin != null ? fin : LocalDate.now();

        if ("CA".equalsIgnoreCase(tri)) {
            return ResponseEntity.ok(statistiquesService.getTopRestaurantsParCA(limit, dateDebut, dateFin));
        }
        return ResponseEntity.ok(statistiquesService.getTopRestaurantsParCommandes(limit, dateDebut, dateFin));
    }

    @GetMapping("/restaurants/performance")
    public ResponseEntity<List<RestaurantPerformanceDTO>> getRestaurantsPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getRestaurantsPerformance(
                debut != null ? debut : LocalDate.now().withDayOfMonth(1),
                fin != null ? fin : LocalDate.now()));
    }

    // ==================== CLIENTS ====================

    @GetMapping("/clients/top")
    public ResponseEntity<List<ClientAnalyticsDTO>> getTopClients(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getTopClients(limit,
                debut != null ? debut : LocalDate.now().minusMonths(3),
                fin != null ? fin : LocalDate.now()));
    }

    @GetMapping("/clients/retention")
    public ResponseEntity<RetentionDTO> getRetention() {
        return ResponseEntity.ok(statistiquesService.getRetentionClients());
    }

    @GetMapping("/clients/par-ville")
    public ResponseEntity<List<ZoneCommandesDTO>> getCommandesParVille(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getCommandesParVille(
                debut != null ? debut : LocalDate.now().withDayOfMonth(1),
                fin != null ? fin : LocalDate.now()));
    }

    // ==================== LIVREURS ====================

    @GetMapping("/livreurs")
    public ResponseEntity<List<LivreurAnalyticsDTO>> getLivreursPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getLivreursPerformance(
                debut != null ? debut : LocalDate.now().withDayOfMonth(1),
                fin != null ? fin : LocalDate.now()));
    }

    @GetMapping("/livreurs/top")
    public ResponseEntity<List<LivreurAnalyticsDTO>> getTopLivreurs(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getTopLivreurs(limit,
                debut != null ? debut : LocalDate.now().withDayOfMonth(1),
                fin != null ? fin : LocalDate.now()));
    }

    // ==================== PLATS ====================

    @GetMapping("/plats/top")
    public ResponseEntity<List<PlatAnalyticsDTO>> getTopPlats(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getTopPlats(limit,
                debut != null ? debut : LocalDate.now().withDayOfMonth(1),
                fin != null ? fin : LocalDate.now()));
    }

    @GetMapping("/plats/jamais-commandes")
    public ResponseEntity<List<PlatAnalyticsDTO>> getPlatsJamaisCommandes() {
        return ResponseEntity.ok(statistiquesService.getPlatsJamaisCommandes());
    }

    @GetMapping("/plats/par-categorie")
    public ResponseEntity<List<PlatAnalyticsDTO>> getPlatsByCategorie(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getCAParCategoriePlat(
                debut != null ? debut : LocalDate.now().withDayOfMonth(1),
                fin != null ? fin : LocalDate.now()));
    }

    // ==================== FINANCE ====================

    @GetMapping("/financier")
    public ResponseEntity<FinancierDTO> getRapportFinancier(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getRapportFinancier(
                debut != null ? debut : LocalDate.now().withDayOfMonth(1),
                fin != null ? fin : LocalDate.now()));
    }

    // ==================== MONITORING ====================

    @GetMapping("/monitoring")
    public ResponseEntity<MonitoringTempsReelDTO> getMonitoring() {
        return ResponseEntity.ok(statistiquesService.getMonitoringTempsReel());
    }

    @GetMapping("/alertes")
    public ResponseEntity<List<AlerteDTO>> getAlertes() {
        return ResponseEntity.ok(statistiquesService.getAlertes());
    }
}
