package ma.mysuguclientapp.legacy.deliveryman.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.DemandeRetrait;
import ma.mysuguclientapp.enumerations.StatutRetrait;
import ma.mysuguclientapp.legacy.deliveryman.dto.MessageResponse;
import ma.mysuguclientapp.repositories.DemandeRetraitRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.GainsLivreurServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Gestion admin des demandes de retrait livreur. Ferme la boucle du modèle argent (techspec §7) :
 * à l'APPROBATION, les gains non payés du livreur sont marqués payés (ce qui réduit current_balance) ;
 * au REFUS, la demande sort de pending sans toucher les gains.
 * L'app livreur ne fait que créer/lister les demandes ; l'admin les traite ici (route /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin/livreurs/retraits")
@RequiredArgsConstructor
public class AdminRetraitLivreurController {

    private final DemandeRetraitRepository demandeRetraitRepository;
    private final GainsLivreurServiceImpl gainsLivreurService;
    private final UserRepository userRepository;

    @GetMapping("/en-attente")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DemandeRetrait> enAttente() {
        return demandeRetraitRepository.findAll().stream()
                .filter(d -> d.getStatut() == StatutRetrait.EN_ATTENTE)
                .toList();
    }

    @PostMapping("/{id}/approuver")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MessageResponse approuver(@AuthenticationPrincipal String email, @PathVariable Long id,
                                     @RequestBody(required = false) Map<String, Object> body) {
        DemandeRetrait d = get(id);
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

    @PostMapping("/{id}/refuser")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MessageResponse refuser(@AuthenticationPrincipal String email, @PathVariable Long id) {
        DemandeRetrait d = get(id);
        if (d.getStatut() != StatutRetrait.EN_ATTENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demande déjà traitée.");
        }
        d.setStatut(StatutRetrait.REFUSE);
        userRepository.findByEmail(email).ifPresent(d::setAdminValidateur);
        demandeRetraitRepository.save(d);
        return new MessageResponse("Retrait refusé.");
    }

    private DemandeRetrait get(Long id) {
        return demandeRetraitRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande introuvable."));
    }
}
