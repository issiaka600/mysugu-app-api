package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ZoneDeploiementDTO {
    private Long id;
    private String nom;
    private String description;
    private Double centreLatitude;
    private Double centreLongitude;
    private BigDecimal rayonKm;
    private BigDecimal fraisLivraisonMin;
    private BigDecimal distanceMinKm;
    private BigDecimal prixExtraParKm;
    private BigDecimal prixParKm;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
