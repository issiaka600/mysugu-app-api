package ma.mysuguclientapp.dtos.cart;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GainsLivreurDTO {
    private Long id;
    private Long commandeId;
    private String numeroCommande;
    private BigDecimal montant;
    private BigDecimal fraisLivraison;
    private BigDecimal commissionPlateforme;
    private BigDecimal montantNet;
    private Boolean estPaye;
    private LocalDateTime payeAt;
    private LocalDateTime createdAt;
}
