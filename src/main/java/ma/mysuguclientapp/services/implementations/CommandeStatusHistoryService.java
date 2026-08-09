package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CommandeStatusHistoryDTO;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.CommandeStatutHistorique;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.repositories.CommandeStatutHistoriqueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommandeStatusHistoryService {

    private final CommandeStatutHistoriqueRepository historiqueRepository;

    /** Enregistre une transition uniquement lorsqu'elle est une étape Customer visible. */
    public void record(Commande commande) {
        record(commande, commande != null ? commande.getStatut() : null);
    }

    /**
     * Utilisé lorsqu'une synchronisation reçoit une étape Customer puis affecte le livreur
     * dans la même transaction : l'affectation ne doit pas effacer l'étape visible reçue.
     */
    public void record(Commande commande, StatutCommande statut) {
        if (commande == null || commande.getId() == null) {
            return;
        }
        String status = normalize(statut);
        if (status == null) {
            return;
        }
        historiqueRepository.save(CommandeStatutHistorique.builder()
                .commande(commande)
                .status(status)
                .changedAt(LocalDateTime.now())
                .build());
    }

    @Transactional(readOnly = true)
    public List<CommandeStatusHistoryDTO> getHistory(Long commandeId) {
        if (commandeId == null) {
            return List.of();
        }
        return historiqueRepository.findByCommandeIdOrderByChangedAtAscIdAsc(commandeId).stream()
                .map(entry -> CommandeStatusHistoryDTO.builder()
                        .status(entry.getStatus())
                        .changedAt(entry.getChangedAt())
                        .build())
                .toList();
    }

    private String normalize(StatutCommande statut) {
        if (statut == null) {
            return null;
        }
        return switch (statut) {
            case EN_ATTENTE -> "pending";
            case CONFIRMEE -> "confirmed";
            case EN_PREPARATION -> "processing";
            case PRETE -> "ready";
            case EN_COURS -> "out_for_delivery";
            case LIVREE -> "delivered";
            // Une annulation et l'assignation ne constituent pas une étape du parcours Customer demandé.
            case ASSIGNEE_LIVREUR, ANNULEE, NON_FINALISEE -> null;
        };
    }
}
