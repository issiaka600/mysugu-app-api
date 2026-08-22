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
    /** Mode de commission appliqué à cette ligne : POURCENTAGE ou FIXE */
    private String commissionType;
    /** Taux appliqué (en %), renseigné seulement en mode POURCENTAGE */
    private java.math.BigDecimal commissionPourcentage;
    /** Montant unitaire appliqué, renseigné seulement en mode FIXE */
    private java.math.BigDecimal commissionMontantFixe;
    private java.math.BigDecimal montantCommission;
    private java.util.List<OptionChoisieDTO> options;
}
