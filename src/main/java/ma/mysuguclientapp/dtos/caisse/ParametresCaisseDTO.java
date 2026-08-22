package ma.mysuguclientapp.dtos.caisse;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ParametresCaisseDTO {
    private BigDecimal plafondCaisseLivreur;
    private Integer seuilAlertePourcentage;
    private Integer intervalleReconciliationHeures;
    /** Commission plateforme sur les frais de livraison, en fraction (0.15 = 15 %) */
    private BigDecimal tauxCommissionPlateforme;
    /** Mode de calcul de la commission sur les frais de livraison : POURCENTAGE ou FIXE */
    private String commissionPlateformeType;
    /** Montant prélevé par course quand commissionPlateformeType vaut FIXE */
    private BigDecimal commissionPlateformeMontantFixe;
    private Integer periodicitePaiementRestaurantJours;
    /** Seuil de prix (en DH) en-dessous duquel la commission minimum globale s'applique */
    private BigDecimal seuilPrixCommission;
    /** Commission minimum globale (en %) pour les plats dont le prix ≤ seuilPrixCommission */
    private BigDecimal commissionMinPourcentage;
    /** Mode de calcul de la commission minimum globale : POURCENTAGE ou FIXE */
    private String commissionMinType;
    /** Montant prélevé par article sous le seuil quand commissionMinType vaut FIXE */
    private BigDecimal commissionMinMontantFixe;
}
