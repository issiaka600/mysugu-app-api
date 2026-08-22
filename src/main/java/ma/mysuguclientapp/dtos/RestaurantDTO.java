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
    private String bannerObjectName;
    private String bannerUrl;
    private Double appreciation;
    private Integer nombreAvis;
    private Integer tempsLivraisonMoyen;
    private LocalisationDTO localisation;
    private CategorieRestaurantDTO categorie;
    private PromotionDTO promotion;
    /** Verticale de service : RESTAURANT, ALIMENTAIRE, COSMETIQUE */
    private String vertical;
    /** Rayons non vides de cet établissement, ordonnés. Vide pour un restaurant. */
    private java.util.List<EnumOptionDTO> rayons = java.util.List.of();
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
    /** Mode de calcul de la commission négociée : POURCENTAGE ou FIXE */
    private String commissionType;
    /** Pourcentage de commission négocié avec ce restaurant (en %), en mode POURCENTAGE */
    private java.math.BigDecimal commissionPourcentage;
    /** Montant de commission prélevé par article, en mode FIXE */
    private java.math.BigDecimal commissionMontantFixe;

    /** Statut d'approbation : EN_ATTENTE, COMPLEMENT_REQUIS, APPROUVE, REJETE */
    private String statutApprobation;
    /** Motif de rejet ou détail de la demande de complément (le cas échéant). */
    private String motifRevue;
    private LocalDateTime dateRevue;
    /** Object names des justificatifs fournis par le restaurateur. */
    private java.util.List<String> justificatifs;
    /** URLs publiques des justificatifs (dérivées des object names). */
    private java.util.List<String> justificatifsUrls;
}
