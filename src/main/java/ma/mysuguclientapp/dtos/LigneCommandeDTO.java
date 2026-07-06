package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LigneCommandeDTO {
    private Long id;
    private PlatDTO plat;
    private Integer quantite;
    private BigDecimal prixUnitaire;
    private BigDecimal montantTotal;
    private String currency = "MAD";
    private String currencySymbol = "DH";
    private String remarque;
    private Integer quantiteLivree;
    private java.math.BigDecimal commissionPourcentage;
    private java.math.BigDecimal montantCommission;
    private java.util.List<OptionChoisieDTO> options;
}
