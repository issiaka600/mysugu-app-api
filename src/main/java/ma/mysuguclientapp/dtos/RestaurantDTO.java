package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class RestaurantDTO {
    private Long id;
    private ZoneDeploiementDTO zoneDeploiement;
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
    /** Verticale de service : RESTAURANT, ALIMENTAIRE, COSMETIQUE */
    private String vertical;
    private Boolean isActive;
    private Boolean autoCloseEnabled;
    private Boolean openNow;
    private LocalTime heureOuverture;
    private LocalTime heureFermeture;
    private LocalDateTime createdAt;
    private Double distance;
    /** Propriétaire (restaurateur) associé au restaurant */
    private Long ownerId;
    private String ownerNom;
    private String ownerPrenom;
    private String ownerEmail;
    /** Pourcentage de commission négocié avec ce restaurant (en %) */
    private java.math.BigDecimal commissionPourcentage;
}
