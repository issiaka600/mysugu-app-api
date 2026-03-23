package ma.mysuguclientapp.dtos.commerce;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WalletDTO {
    private Long id;
    private Long userId;
    private String userNom;
    private String userPrenom;
    private BigDecimal solde;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
