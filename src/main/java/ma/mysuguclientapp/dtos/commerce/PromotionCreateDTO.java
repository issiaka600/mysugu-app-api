package ma.mysuguclientapp.dtos.commerce;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PromotionCreateDTO {
    private Integer pourcentage;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String description;
    private Long restaurantId;
    private String code;
    private BigDecimal montantMinCommande;
    private Integer usageMax;
    private Boolean estFlash;
    private Boolean appliquerATousLesRestaurants;
}
