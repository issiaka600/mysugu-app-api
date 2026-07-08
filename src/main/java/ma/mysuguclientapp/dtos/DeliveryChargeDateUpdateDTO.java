package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DeliveryChargeDateUpdateDTO {
    private BigDecimal fraisLivraison;
    private LocalDateTime dateLivraisonPrevue;
    private String causeReport;
}