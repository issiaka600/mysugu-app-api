package ma.mysuguclientapp.dtos.stats;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FinancierDTO {
    private BigDecimal revenuBrut;
    private BigDecimal totalFraisLivraison;
    private BigDecimal commissionPlateforme;
    private BigDecimal revenuNet;
    private Long commandesPaye;
    private Long commandesRembourse;
}
