package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class RestaurantCreateDTO {
    private String nom;
    private String description;
    private Long categorieId;
    private Long ownerId;
    private LocalisationDTO localisation;
    private String horairesOuverture;
    private Integer tempsLivraisonMoyen;
}