package ma.mysuguclientapp.dtos.commerce;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PromotionDTO {
    private Long id;
    private Integer pourcentage;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String description;
    private Boolean isActive;
    private Long restaurantId;
    private String restaurantNom;
    private String code;
    private BigDecimal montantMinCommande;
    private Integer usageMax;
    private Integer usageCount;
    private Boolean estFlash;
}
