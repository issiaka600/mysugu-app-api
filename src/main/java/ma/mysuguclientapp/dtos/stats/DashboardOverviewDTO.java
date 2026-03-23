package ma.mysuguclientapp.dtos.stats;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DashboardOverviewDTO {
    private Long commandesTotalAujourdhui;
    private Long commandesTotalSemaine;
    private Long commandesTotalMois;
    private BigDecimal chiffreAffairesAujourdhui;
    private BigDecimal chiffreAffairesSemaine;
    private BigDecimal chiffreAffairesMois;
    private Double tauxAnnulation;
    private BigDecimal valeurMoyenneCommande;
    private Long nouveauxUsersAujourdhui;
    private Long nouveauxUsersSemaine;
    private Long nouveauxUsersMois;
    private Long commandesEnCours;
    private Long restaurantsActifs;
    private Long livreursActifs;
}
