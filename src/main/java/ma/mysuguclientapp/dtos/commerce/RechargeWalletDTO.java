package ma.mysuguclientapp.dtos.commerce;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RechargeWalletDTO {
    private BigDecimal montant;
    private String referenceExterne;
    private String description;
}
