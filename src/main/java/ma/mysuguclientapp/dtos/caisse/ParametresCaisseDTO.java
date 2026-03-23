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
}
