package ma.mysuguclientapp.dtos;

import lombok.Data;
import ma.mysuguclientapp.enumerations.StatutPaiement;

@Data
public class UpdatePaymentStatusDTO {
    private StatutPaiement statutPaiement;
}