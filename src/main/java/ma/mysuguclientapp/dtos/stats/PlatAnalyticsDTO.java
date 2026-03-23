package ma.mysuguclientapp.dtos.stats;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PlatAnalyticsDTO {
    private Long platId;
    private String nom;
    private String restaurantNom;
    private String categorie;
    private Long nombreCommandes;
    private Long quantiteTotale;
    private BigDecimal chiffreAffaires;
}
