package ma.mysuguclientapp.dtos.stats;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LivreurAnalyticsDTO {
    private Long livreurId;
    private String nom;
    private String prenom;
    private Long nombreLivraisons;
    private BigDecimal totalFraisLivraison;
    private Double tempsLivraisonMoyen;
    private Double distanceTotaleParcourue;
}
