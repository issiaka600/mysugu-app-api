package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class RestaurantDTO {
    private Long id;
    private String nom;
    private String description;
    private String logoObjectName;
    private String logoUrl;
    private Double appreciation;
    private Integer nombreAvis;
    private Integer tempsLivraisonMoyen;
    private LocalisationDTO localisation;
    private CategorieRestaurantDTO categorie;
    private PromotionDTO promotion;
    private Boolean isActive;
    private Boolean autoCloseEnabled;
    private Boolean openNow;
    private LocalTime heureOuverture;
    private LocalTime heureFermeture;
    private LocalDateTime createdAt;
    private Double distance;
}
