package ma.mysuguclientapp.dtos.commerce;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ResultatCodePromoDTO {
    private boolean valide;
    private String message;
    private BigDecimal montantOriginal;
    private BigDecimal montantReduit;
    private BigDecimal montantFinal;
    private String codePromo;
}
