package ma.mysuguclientapp.dtos.commerce;

import lombok.Data;
import ma.mysuguclientapp.enumerations.TypeReduction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CodePromoCreateDTO {
    private String code;
    private String description;
    private TypeReduction typeReduction;
    private BigDecimal valeur;
    private BigDecimal montantMinCommande;
    private BigDecimal montantMaxReduction;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private Integer usageMax;
}
