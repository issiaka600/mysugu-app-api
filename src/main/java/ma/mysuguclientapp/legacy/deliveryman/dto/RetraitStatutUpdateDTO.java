package ma.mysuguclientapp.legacy.deliveryman.dto;

import lombok.Data;
import ma.mysuguclientapp.enumerations.StatutRetrait;

@Data
public class RetraitStatutUpdateDTO {
    private StatutRetrait statut;
    private String transactionRef;
}