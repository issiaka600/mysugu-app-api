package ma.mysuguclientapp.dtos.caisse;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AvanceRequestDTO {
    private BigDecimal montant;
    private String note;
}
