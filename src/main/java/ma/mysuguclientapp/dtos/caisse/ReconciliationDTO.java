package ma.mysuguclientapp.dtos.caisse;

import lombok.Data;

import java.math.BigDecimal;

/** Requête d'assainissement de la caisse d'un livreur (admin → livreur se présente) */
@Data
public class ReconciliationDTO {
    private Long livreurId;
    /** Montant physiquement remis par le livreur à l'admin */
    private BigDecimal montantRemis;
    private String note;
}
