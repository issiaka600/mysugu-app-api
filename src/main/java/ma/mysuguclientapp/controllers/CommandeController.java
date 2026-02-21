package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CommandeCreateDTO;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.CommandeUpdateStatusDTO;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/commandes")
@RequiredArgsConstructor
public class CommandeController {

    private final CommandeService commandeService;

    /**
     * GET /api/commandes - Obtenir toutes les commandes
     */
    @GetMapping
    public ResponseEntity<Page<CommandeDTO>> getAllCommandes(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) StatutCommande statut,
            Pageable pageable) {
        
        Page<CommandeDTO> commandes = commandeService.getAllCommandes(clientId, restaurantId, statut, pageable);
        return ResponseEntity.ok(commandes);
    }

    /**
     * GET /api/commandes/{id} - Obtenir une commande par ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<CommandeDTO> getCommandeById(@PathVariable Long id) {
        CommandeDTO commande = commandeService.getCommandeById(id);
        return ResponseEntity.ok(commande);
    }

    /**
     * GET /api/commandes/numero/{numeroCommande} - Par numéro de commande
     */
    @GetMapping("/numero/{numeroCommande}")
    public ResponseEntity<CommandeDTO> getCommandeByNumero(@PathVariable String numeroCommande) {
        CommandeDTO commande = commandeService.getCommandeByNumero(numeroCommande);
        return ResponseEntity.ok(commande);
    }

    /**
     * GET /api/commandes/client/{clientId} - Commandes d'un client
     */
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<CommandeDTO>> getCommandesByClient(@PathVariable Long clientId) {
        List<CommandeDTO> commandes = commandeService.getCommandesByClient(clientId);
        return ResponseEntity.ok(commandes);
    }

    /**
     * GET /api/commandes/restaurant/{restaurantId} - Commandes d'un restaurant
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<CommandeDTO>> getCommandesByRestaurant(@PathVariable Long restaurantId) {
        List<CommandeDTO> commandes = commandeService.getCommandesByRestaurant(restaurantId);
        return ResponseEntity.ok(commandes);
    }

    /**
     * GET /api/commandes/livreur/{livreurId} - Commandes d'un livreur
     */
    @GetMapping("/livreur/{livreurId}")
    public ResponseEntity<List<CommandeDTO>> getCommandesByLivreur(@PathVariable Long livreurId) {
        List<CommandeDTO> commandes = commandeService.getCommandesByLivreur(livreurId);
        return ResponseEntity.ok(commandes);
    }

    /**
     * GET /api/commandes/en-cours - Commandes en cours
     */
    @GetMapping("/en-cours")
    public ResponseEntity<List<CommandeDTO>> getCommandesEnCours() {
        List<CommandeDTO> commandes = commandeService.getCommandesEnCours();
        return ResponseEntity.ok(commandes);
    }

    /**
     * POST /api/commandes - Créer une nouvelle commande
     */
    @PostMapping
    public ResponseEntity<CommandeDTO> createCommande(@Valid @RequestBody CommandeCreateDTO commandeDTO) {
        CommandeDTO created = commandeService.createCommande(commandeDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PATCH /api/commandes/{id}/status - Mettre à jour le statut
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<CommandeDTO> updateCommandeStatus(
            @PathVariable Long id,
            @Valid @RequestBody CommandeUpdateStatusDTO statusDTO) {
        
        CommandeDTO updated = commandeService.updateCommandeStatus(id, statusDTO);
        return ResponseEntity.ok(updated);
    }

    /**
     * PATCH /api/commandes/{id}/assign-livreur/{livreurId} - Assigner un livreur
     */
    @PatchMapping("/{id}/assign-livreur/{livreurId}")
    public ResponseEntity<CommandeDTO> assignLivreur(
            @PathVariable Long id,
            @PathVariable Long livreurId) {
        
        CommandeDTO updated = commandeService.assignLivreur(id, livreurId);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/commandes/{id} - Annuler une commande
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<CommandeDTO> cancelCommande(@PathVariable Long id) {
        CommandeDTO cancelled = commandeService.cancelCommande(id);
        return ResponseEntity.ok(cancelled);
    }

    /**
     * GET /api/commandes/{id}/tracking - Suivi de commande
     */
    @GetMapping("/{id}/tracking")
    public ResponseEntity<?> trackCommande(@PathVariable Long id) {
        // Retourne les informations de tracking (position livreur, etc.)
        return ResponseEntity.ok(commandeService.getCommandeTracking(id));
    }
}
