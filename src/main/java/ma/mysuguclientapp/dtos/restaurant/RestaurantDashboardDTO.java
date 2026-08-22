package ma.mysuguclientapp.dtos.restaurant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RestaurantDashboardDTO {
    private Long restaurantId;
    private String restaurantNom;

    // Today stats
    private Long commandesAujourdhui;
    private Long commandesLivreesAujourdhui;
    private Long commandesAnnuleesAujourdhui;
    private BigDecimal chiffreAffairesAujourdhui;
    private BigDecimal commissionAujourdhui;

    // This week
    private Long commandesSemaine;
    private BigDecimal chiffreAffairesSemaine;
    private BigDecimal commissionSemaine;

    // This month
    private Long commandesMois;
    private BigDecimal chiffreAffairesMois;
    private BigDecimal commissionMois;

    // Overall
    private Double appreciation;
    private Integer nombreAvis;
    private Long totalCommandes;
    private BigDecimal totalChiffreAffaires;
    /**
     * Commissions effectivement prélevées, cumulées depuis les commandes livrées.
     * Portées ici plutôt que recalculées à partir d'un taux : une commission peut être un
     * montant fixe, ou provenir du barème global sous le seuil de prix.
     */
    private BigDecimal totalCommission;

    // Current activity
    private Long commandesEnCours;
    private Long commandesEnPreparation;
}
