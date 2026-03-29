package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.time.LocalTime;

@Data
public class RestaurantCreateDTO {
    private String nom;
    private String description;
    private Long categorieId;
    private Long ownerId;
    private LocalisationDTO localisation;
    private String horairesOuverture;
    private Integer tempsLivraisonMoyen;
    private Boolean autoCloseEnabled;
    private LocalTime heureOuverture;
    private LocalTime heureFermeture;
    private Boolean removeLogo;

    /**
     * Identifiant de la zone de déploiement à laquelle rattacher ce restaurant.
     * Obligatoire dès que le restaurant propose la livraison.
     */
    private Long zoneDeploiementId;
}
