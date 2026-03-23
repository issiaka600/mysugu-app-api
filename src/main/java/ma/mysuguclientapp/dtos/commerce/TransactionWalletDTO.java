package ma.mysuguclientapp.dtos.commerce;

import lombok.Data;
import ma.mysuguclientapp.enumerations.TypeTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionWalletDTO {
    private Long id;
    private TypeTransaction type;
    private BigDecimal montant;
    private BigDecimal soldeAvant;
    private BigDecimal soldeApres;
    private String description;
    private String referenceExterne;
    private LocalDateTime createdAt;
}
