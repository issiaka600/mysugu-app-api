package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RestaurantDTO {
    private Long id;
    private String nom;
    private String description;
    private String logoUrl;
    private Double appreciation;
    private Integer nombreAvis;
    private Integer tempsLivraisonMoyen;
    private LocalisationDTO localisation;
    private CategorieRestaurantDTO categorie;
    private PromotionDTO promotion;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private Double distance; // Distance calculée par rapport à l'utilisateur
}
