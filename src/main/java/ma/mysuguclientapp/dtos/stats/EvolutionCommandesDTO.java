package ma.mysuguclientapp.dtos.stats;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class EvolutionCommandesDTO {
    private String periode;
    private Long nombreCommandes;
    private BigDecimal chiffreAffaires;
    private Long commandesLivrees;
    private Long commandesAnnulees;
}
