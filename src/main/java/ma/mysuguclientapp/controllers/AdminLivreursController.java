package ma.mysuguclientapp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.ContactUrgenceCreateDTO;
import ma.mysuguclientapp.dtos.ContactUrgenceDTO;
import ma.mysuguclientapp.dtos.ContactUrgenceStatutUpdateDTO;
import ma.mysuguclientapp.dtos.admin.LivreurDetailDTO;
import ma.mysuguclientapp.entities.DemandeRetrait;
import ma.mysuguclientapp.enumerations.StatutRetrait;
import ma.mysuguclientapp.legacy.deliveryman.dto.MessageResponse;
import ma.mysuguclientapp.legacy.deliveryman.dto.RetraitStatutUpdateDTO;
import ma.mysuguclientapp.repositories.DemandeRetraitRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.GainsLivreurServiceImpl;
import ma.mysuguclientapp.services.interfaces.AdminLivreurService;
import ma.mysuguclientapp.services.interfaces.ContactUrgenceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur admin unique regroupant tout ce qui touche à la gestion des livreurs :
 * - Détails / suppression d'un livreur
 * - Demandes de retrait (liste, détails, approbation/refus, statut générique)
 * - Contacts d'urgence (rattachés à un restaurant, consultés en lecture par les livreurs
 *   via GET /api/v2/delivery-man/emergency-contact-list)
 *
 * Ferme la boucle du modèle argent (techspec §7) : à l'APPROBATION d'un retrait, les gains
 * non payés du livreur sont considérés payés (ce qui réduit current_balance) ; au REFUS,
 * la demande sort de "pending" sans toucher les gains.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLivreursController {

    private final AdminLivreurService adminLivreurService;
    private final ContactUrgenceService contactUrgenceService;
    private final DemandeRetraitRepository demandeRetraitRepository;
    private final UserRepository userRepository;

    // =====================================================================
    // Livreurs — détails / suppression
    // =====================================================================

    /**
     * GET /api/admin/livreurs/{id} - Détails enrichis d'un livreur (stats + argent)
     */
    @GetMapping("/livreurs/{id}")
    public ResponseEntity<LivreurDetailDTO> getLivreurDetails(@PathVariable Long id) {
        return ResponseEntity.ok(adminLivreurService.getLivreurDetails(id));
    }

    /**
     * DELETE /api/admin/livreurs/{id} - Supprime (soft-delete) un livreur.
     * Refuse si le livreur a des livraisons en cours (ASSIGNEE_LIVREUR, EN_COURS).
     */
    @DeleteMapping("/livreurs/{id}")
    public ResponseEntity<Void> deleteLivreur(@PathVariable Long id) {
        adminLivreurService.deleteLivreur(id);
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // Retraits
    // =====================================================================

    /**
     * GET /api/admin/livreurs/retraits?statut=EN_ATTENTE - Liste complète (filtrable par statut)
     */
    @GetMapping("/livreurs/retraits")
    public List<DemandeRetrait> listeRetraits(@RequestParam(required = false) StatutRetrait statut) {
        if (statut != null) {
            return demandeRetraitRepository.findAll().stream()
                    .filter(d -> d.getStatut() == statut)
                    .toList();
        }
        return demandeRetraitRepository.findAll();
    }

    /**
     * GET /api/admin/livreurs/retraits/{id} - Détails d'une demande de retrait
     */
    @GetMapping("/livreurs/retraits/{id}")
    public DemandeRetrait detailsRetrait(@PathVariable Long id) {
        return getRetrait(id);
    }

    /**
     * GET /api/admin/livreurs/retraits/en-attente - Raccourci historique (liste avec statut=EN_ATTENTE)
     */
    @GetMapping("/livreurs/retraits/en-attente")
    public List<DemandeRetrait> retraitsEnAttente() {
        return demandeRetraitRepository.findAll().stream()
                .filter(d -> d.getStatut() == StatutRetrait.EN_ATTENTE)
                .toList();
    }

    /**
     * PATCH /api/admin/livreurs/retraits/{id}/statut - Mise à jour générique du statut.
     * Équivalent générique de /approuver et /refuser (mêmes garde-fous), pour un client
     * qui préfère piloter le statut directement plutôt que d'appeler une action dédiée.
     */
    @PatchMapping("/livreurs/retraits/{id}/statut")
    @Transactional
    public MessageResponse statutUpdateRetrait(@AuthenticationPrincipal String email, @PathVariable Long id,
                                               @RequestBody RetraitStatutUpdateDTO dto) {
        if (dto.getStatut() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le statut est requis.");
        }
        DemandeRetrait d = getRetrait(id);
        if (d.getStatut() != StatutRetrait.EN_ATTENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demande déjà traitée.");
        }

        d.setStatut(dto.getStatut());
        if (dto.getTransactionRef() != null) {
            d.setTransactionRef(dto.getTransactionRef());
        }
        userRepository.findByEmail(email).ifPresent(d::setAdminValidateur);
        demandeRetraitRepository.save(d);

        return new MessageResponse("Statut de la demande mis a jour: " + dto.getStatut());
    }

    @PostMapping("/livreurs/retraits/{id}/approuver")
    @Transactional
    public MessageResponse approuverRetrait(@AuthenticationPrincipal String email, @PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        DemandeRetrait d = getRetrait(id);
        if (d.getStatut() != StatutRetrait.EN_ATTENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demande déjà traitée.");
        }
        d.setStatut(StatutRetrait.APPROUVE);
        if (body != null && body.get("transaction_ref") != null) d.setTransactionRef(body.get("transaction_ref").toString());
        userRepository.findByEmail(email).ifPresent(d::setAdminValidateur);
        demandeRetraitRepository.save(d);
        // current_balance = Σ gains nets − Σ retraits APPROUVE : passer ce retrait en APPROUVE
        // réduit automatiquement current_balance du montant retiré (gère les retraits partiels).
        return new MessageResponse("Retrait approuvé.");
    }

    @PostMapping("/livreurs/retraits/{id}/refuser")
    @Transactional
    public MessageResponse refuserRetrait(@AuthenticationPrincipal String email, @PathVariable Long id) {
        DemandeRetrait d = getRetrait(id);
        if (d.getStatut() != StatutRetrait.EN_ATTENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demande déjà traitée.");
        }
        d.setStatut(StatutRetrait.REFUSE);
        userRepository.findByEmail(email).ifPresent(d::setAdminValidateur);
        demandeRetraitRepository.save(d);
        return new MessageResponse("Retrait refusé.");
    }

    private DemandeRetrait getRetrait(Long id) {
        return demandeRetraitRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande introuvable."));
    }

    // =====================================================================
    // Contacts d'urgence
    // =====================================================================

    /**
     * GET /api/admin/contacts-urgence?restaurantId=5 - Liste (filtrable par restaurant)
     */
    @GetMapping("/contacts-urgence")
    public ResponseEntity<List<ContactUrgenceDTO>> listeContacts(@RequestParam(required = false) Long restaurantId) {
        return ResponseEntity.ok(contactUrgenceService.getAll(restaurantId));
    }

    /**
     * POST /api/admin/contacts-urgence - Créer un contact d'urgence
     */
    @PostMapping("/contacts-urgence")
    public ResponseEntity<ContactUrgenceDTO> creerContact(@Valid @RequestBody ContactUrgenceCreateDTO dto) {
        ContactUrgenceDTO created = contactUrgenceService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/admin/contacts-urgence/{id} - Mettre à jour un contact d'urgence
     */
    @PutMapping("/contacts-urgence/{id}")
    public ResponseEntity<ContactUrgenceDTO> modifierContact(
            @PathVariable Long id,
            @Valid @RequestBody ContactUrgenceCreateDTO dto) {
        return ResponseEntity.ok(contactUrgenceService.update(id, dto));
    }

    /**
     * PATCH /api/admin/contacts-urgence/{id}/statut - Activer/désactiver un contact
     */
    @PatchMapping("/contacts-urgence/{id}/statut")
    public ResponseEntity<ContactUrgenceDTO> statutContact(
            @PathVariable Long id,
            @Valid @RequestBody ContactUrgenceStatutUpdateDTO dto) {
        return ResponseEntity.ok(contactUrgenceService.updateStatut(id, dto));
    }

    /**
     * DELETE /api/admin/contacts-urgence/{id} - Supprimer un contact d'urgence
     */
    @DeleteMapping("/contacts-urgence/{id}")
    public ResponseEntity<Void> supprimerContact(@PathVariable Long id) {
        contactUrgenceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}