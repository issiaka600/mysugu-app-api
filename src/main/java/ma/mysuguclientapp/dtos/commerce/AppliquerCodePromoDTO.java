package ma.mysuguclientapp.dtos.commerce;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AppliquerCodePromoDTO {
    private String code;
    private BigDecimal montantCommande;
}
