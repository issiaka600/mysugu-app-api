package ma.mysuguclientapp.dtos.restaurant;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ZoneLivraisonDTO {
    private Long id;
    private Long restaurantId;
    private String nom;
    private BigDecimal rayonKm;
    private BigDecimal fraisLivraison;
    private BigDecimal montantMinCommande;
    private Integer tempsEstimeMinutes;
    private Boolean isActive;
    private Double centreLatitude;
    private Double centreLongitude;
}
