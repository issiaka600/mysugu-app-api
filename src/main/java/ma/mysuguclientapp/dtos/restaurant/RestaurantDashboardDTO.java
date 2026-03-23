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

    // This week
    private Long commandesSemaine;
    private BigDecimal chiffreAffairesSemaine;

    // This month
    private Long commandesMois;
    private BigDecimal chiffreAffairesMois;

    // Overall
    private Double appreciation;
    private Integer nombreAvis;
    private Long totalCommandes;
    private BigDecimal totalChiffreAffaires;

    // Current activity
    private Long commandesEnCours;
    private Long commandesEnPreparation;
}
