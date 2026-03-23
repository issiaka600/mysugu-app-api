package ma.mysuguclientapp.dtos.commerce;

import lombok.Data;
import ma.mysuguclientapp.enumerations.TypeTransactionPoints;

import java.time.LocalDateTime;

@Data
public class TransactionPointsDTO {
    private Long id;
    private TypeTransactionPoints type;
    private Integer points;
    private String description;
    private String referenceCommande;
    private LocalDateTime createdAt;
}
