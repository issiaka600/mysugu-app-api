package ma.mysuguclientapp.dtos.caisse;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ParametresCaisseDTO {
    private BigDecimal plafondCaisseLivreur;
    private Integer seuilAlertePourcentage;
    private Integer intervalleReconciliationHeures;
    private BigDecimal tauxCommissionPlateforme;
    private Integer periodicitePaiementRestaurantJours;
    /** Seuil de prix (en DH) en-dessous duquel la commission minimum globale s'applique */
    private BigDecimal seuilPrixCommission;
    /** Commission minimum globale (en %) pour les plats dont le prix ≤ seuilPrixCommission */
    private BigDecimal commissionMinPourcentage;
}
