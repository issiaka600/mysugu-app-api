package ma.mysuguclientapp.dtos.caisse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CaisseLivreurDTO {
    private Long id;
    private Long livreurId;
    private String livreurNom;
    private String livreurPrenom;
    private String livreurTelephone;

    /** Espèces physiquement dans la poche du livreur */
    private BigDecimal soldeCourant;

    /** Gains non encore versés que la plateforme doit au livreur */
    private BigDecimal gainsDusNonVerses;

    /**
     * Ce que le livreur doit remettre à la plateforme = soldeCourant - gainsDusNonVerses.
     * Positif → livreur doit de l'argent.
     * Négatif → plateforme doit de l'argent au livreur.
     */
    private BigDecimal montantDuPlateforme;

    private BigDecimal plafondEffectif;
    private BigDecimal seuilAlerte;

    /** true si soldeCourant >= seuilAlerte */
    private boolean alertePlafond;

    /** true si délai de réconciliation dépassé */
    private boolean alerteIntervalle;

    private LocalDateTime derniereReconciliation;
    private LocalDateTime updatedAt;
}
