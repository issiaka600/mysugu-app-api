package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CommandeCreateDTO;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.CommandeContactsDTO;
import ma.mysuguclientapp.dtos.CommandeUpdateStatusDTO;
import ma.mysuguclientapp.dtos.CancelCommandeRequestDTO;
import ma.mysuguclientapp.dtos.AssignThirdPartyDeliveryDTO;
import ma.mysuguclientapp.dtos.UpdatePaymentStatusDTO;
import ma.mysuguclientapp.dtos.DeliveryChargeDateUpdateDTO;
import ma.mysuguclientapp.dtos.OrderWiseProductUploadDTO;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import ma.mysuguclientapp.services.implementations.CommandeContactService;
import ma.mysuguclientapp.services.implementations.CommandeAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/commandes")
@RequiredArgsConstructor
public class CommandeController {

    private final CommandeService commandeService;
    private final CommandeContactService commandeContactService;
    private final CommandeAccessService commandeAccessService;

    /**
     * GET /api/commandes - Obtenir toutes les commandes
     */
    @GetMapping
    public ResponseEntity<Page<CommandeDTO>> getAllCommandes(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) StatutCommande statut,
            @RequestParam(required = false) ma.mysuguclientapp.enumerations.Vertical vertical,
            Pageable pageable) {

        Page<CommandeDTO> commandes = commandeService.getAllCommandes(clientId, restaurantId, statut, vertical, pageable);
        return ResponseEntity.ok(commandes);
    }

    /**
     * GET /api/commandes/{id} - Obtenir une commande par ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<CommandeDTO> getCommandeById(@PathVariable Long id,
                                                        @AuthenticationPrincipal String email) {
        commandeAccessService.requireOrderAccess(email, id);
        CommandeDTO commande = commandeService.getCommandeById(id);
        return ResponseEntity.ok(commande);
    }

    /**
     * Contacts utilisables pour l'appel ou la messagerie d'une commande. Le serveur déduit
     * toujours l'utilisateur courant du JWT et ne fait jamais confiance à un id utilisateur
     * envoyé par l'application.
     */
    @GetMapping("/{id}/contacts")
    public ResponseEntity<CommandeContactsDTO> getCommandeContacts(
            @PathVariable Long id,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(commandeContactService.getContacts(id, email));
    }

    /**
     * GET /api/commandes/numero/{numeroCommande} - Par numéro de commande
     */
    @GetMapping("/numero/{numeroCommande}")
    public ResponseEntity<CommandeDTO> getCommandeByNumero(@PathVariable String numeroCommande,
                                                            @AuthenticationPrincipal String email) {
        commandeAccessService.requireOrderAccessByNumero(email, numeroCommande);
        CommandeDTO commande = commandeService.getCommandeByNumero(numeroCommande);
        return ResponseEntity.ok(commande);
    }

    /**
     * GET /api/commandes/client/{clientId} - Commandes d'un client
     */
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<CommandeDTO>> getCommandesByClient(@PathVariable Long clientId,
                                                                    @AuthenticationPrincipal String email) {
        commandeAccessService.requireClientListAccess(email, clientId);
        List<CommandeDTO> commandes = commandeService.getCommandesByClient(clientId);
        return ResponseEntity.ok(commandes);
    }

    /**
     * GET /api/commandes/restaurant/{restaurantId} - Commandes d'un restaurant
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<CommandeDTO>> getCommandesByRestaurant(@PathVariable Long restaurantId,
                                                                        @AuthenticationPrincipal String email) {
        commandeAccessService.requireRestaurantListAccess(email, restaurantId);
        List<CommandeDTO> commandes = commandeService.getCommandesByRestaurant(restaurantId);
        return ResponseEntity.ok(commandes);
    }

    /**
     * GET /api/commandes/livreur/{livreurId} - Commandes d'un livreur
     */
    @GetMapping("/livreur/{livreurId}")
    public ResponseEntity<List<CommandeDTO>> getCommandesByLivreur(@PathVariable Long livreurId,
                                                                     @AuthenticationPrincipal String email) {
        commandeAccessService.requireLivreurListAccess(email, livreurId);
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
     * GET /api/commandes/devis - Devis (aperçu) des frais de livraison et de la remise
     * automatique restaurant AVANT de passer la commande (correction PDF "Problème de montant
     * total" : le panier doit afficher le même total que celui facturé après validation).
     * latitude/longitude optionnels : sans adresse encore choisie, fraisLivraison vaut 0.
     */
    @GetMapping("/devis")
    public ResponseEntity<ma.mysuguclientapp.dtos.DevisLivraisonDTO> getDevis(
            @RequestParam Long restaurantId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) java.math.BigDecimal sousTotal) {
        return ResponseEntity.ok(commandeService.calculerDevis(restaurantId, latitude, longitude, sousTotal));
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
     * PATCH /api/commandes/{id}/assign-third-party-delivery - Assigner un livreur tiers (hors plateforme)
     */
    @PatchMapping("/{id}/assign-third-party-delivery")
    public ResponseEntity<CommandeDTO> assignThirdPartyDelivery(
            @PathVariable Long id,
            @Valid @RequestBody AssignThirdPartyDeliveryDTO dto) {

        CommandeDTO updated = commandeService.assignThirdPartyDelivery(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * PATCH /api/commandes/{id}/statut-paiement - Mettre à jour le statut de paiement
     */
    @PatchMapping("/{id}/statut-paiement")
    public ResponseEntity<CommandeDTO> updatePaymentStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePaymentStatusDTO dto) {

        CommandeDTO updated = commandeService.updatePaymentStatus(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * PATCH /api/commandes/{id}/livraison-frais-date - Mettre à jour les frais et/ou la date de livraison prévue
     */
    @PatchMapping("/{id}/livraison-frais-date")
    public ResponseEntity<CommandeDTO> updateDeliveryChargeAndDate(
            @PathVariable Long id,
            @Valid @RequestBody DeliveryChargeDateUpdateDTO dto) {

        CommandeDTO updated = commandeService.updateDeliveryChargeAndDate(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * POST /api/commandes/{id}/order-wise-product-upload - Déclarer les quantités réellement livrées
     */
    @PostMapping("/{id}/order-wise-product-upload")
    public ResponseEntity<CommandeDTO> uploadOrderWiseProducts(
            @PathVariable Long id,
            @Valid @RequestBody OrderWiseProductUploadDTO dto) {

        CommandeDTO updated = commandeService.uploadOrderWiseProducts(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/commandes/{id} - Annuler une commande
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<CommandeDTO> cancelCommande(
            @PathVariable Long id,
            @AuthenticationPrincipal String email,
            @Valid @RequestBody CancelCommandeRequestDTO request) {
        CommandeDTO cancelled = commandeService.cancelCommandeByCustomer(id, email, request.getReason());
        return ResponseEntity.ok(cancelled);
    }

    /**
     * GET /api/commandes/{id}/tracking - Suivi de commande
     */
    @GetMapping("/{id}/tracking")
    public ResponseEntity<?> trackCommande(@PathVariable Long id, @AuthenticationPrincipal String email) {
        commandeAccessService.requireOrderAccess(email, id);
        // Retourne les informations de tracking (position livreur, etc.)
        return ResponseEntity.ok(commandeService.getCommandeTracking(id));
    }
}
