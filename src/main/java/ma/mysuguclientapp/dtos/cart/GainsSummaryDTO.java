package ma.mysuguclientapp.dtos.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class GainsSummaryDTO {
    private BigDecimal totalGains;
    private BigDecimal gainsAujourdhui;
    private BigDecimal gainsSemaine;
    private BigDecimal gainsMois;
    private Long nombreLivraisons;
}
