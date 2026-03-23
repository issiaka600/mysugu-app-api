package ma.mysuguclientapp.dtos.stats;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ZoneCommandesDTO {
    private String ville;
    private Long nombreCommandes;
    private BigDecimal chiffreAffaires;
    private Double pourcentage;
}
