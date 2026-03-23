package ma.mysuguclientapp.dtos.caisse;

import lombok.Data;
import ma.mysuguclientapp.enumerations.TypeTransactionCaisse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionCaisseDTO {
    private Long id;
    private TypeTransactionCaisse type;
    private BigDecimal montant;
    private BigDecimal soldeAvant;
    private BigDecimal soldeApres;
    private String description;
    private Long commandeId;
    private String numeroCommande;
    private String confirmeParNom;
    private LocalDateTime createdAt;
}
