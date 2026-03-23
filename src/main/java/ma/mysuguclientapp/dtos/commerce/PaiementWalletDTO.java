package ma.mysuguclientapp.dtos.commerce;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaiementWalletDTO {
    private BigDecimal montant;
    private Long commandeId;
    private String description;
}
