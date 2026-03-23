package ma.mysuguclientapp.dtos.cart;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PanierItemDTO {
    private Long id;
    private Long platId;
    private String platNom;
    private String platImageUrl;
    private Integer quantite;
    private BigDecimal prixUnitaire;
    private BigDecimal sousTotal;
    private String remarque;
}
