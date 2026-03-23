package ma.mysuguclientapp.dtos.caisse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Résultat de la réconciliation */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ReconciliationResultDTO {
    private Long livreurId;
    private String livreurNom;

    /** Solde avant réconciliation */
    private BigDecimal soldeCourantAvant;

    /** Gains dus au livreur (plateforme → livreur) */
    private BigDecimal gainsDus;

    /** Montant net que le livreur devait remettre */
    private BigDecimal montantDuCalcule;

    /** Montant effectivement remis */
    private BigDecimal montantRemis;

    /**
     * Écart entre calculé et remis.
     * Positif → livreur a remis moins que prévu (livreur doit encore).
     * Négatif → livreur a remis plus (remboursement dû).
     */
    private BigDecimal ecart;

    private LocalDateTime dateReconciliation;
    private String message;
}
