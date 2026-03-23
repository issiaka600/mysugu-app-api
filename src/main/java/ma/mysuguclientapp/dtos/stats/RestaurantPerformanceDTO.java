package ma.mysuguclientapp.dtos.stats;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RestaurantPerformanceDTO {
    private Long restaurantId;
    private String nom;
    private Long nombreCommandes;
    private Long commandesLivrees;
    private Long commandesAnnulees;
    private Double tauxAnnulation;
    private BigDecimal chiffreAffaires;
    private Double tempsPreparationMoyen;
}
