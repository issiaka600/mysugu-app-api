package ma.mysuguclientapp.dtos.stats;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TopRestaurantDTO {
    private Long restaurantId;
    private String nom;
    private Long nombreCommandes;
    private BigDecimal chiffreAffaires;
    private Double appreciation;
    private Long nombreAvis;
    private Double tauxValidation;
}
