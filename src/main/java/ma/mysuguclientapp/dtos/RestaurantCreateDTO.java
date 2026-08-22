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

    /** Verticale de service (RESTAURANT par défaut si absent) : RESTAURANT, ALIMENTAIRE, COSMETIQUE */
    private String vertical;

    /**
     * Adresse de l'établissement. Les trois champs {@code localisation.*} restent acceptés,
     * mais les formulaires postent des champs à plat : sans ces alias, l'adresse saisie
     * était silencieusement perdue et l'établissement créé sans coordonnées.
     */
    private String adresse;
    private String ville;
    private String codePostal;
    private String pays;
    private Double latitude;
    private Double longitude;

    /**
     * Commission négociée, posée dès la création depuis le back-office.
     * {@code commissionType} vaut POURCENTAGE (défaut) ou FIXE ; la valeur utile est
     * {@code commissionPourcentage} dans le premier cas, {@code commissionMontantFixe}
     * dans le second. Les trois absents ⇒ la commission n'est pas modifiée.
     */
    private String commissionType;
    private java.math.BigDecimal commissionPourcentage;
    private java.math.BigDecimal commissionMontantFixe;

    /**
     * Données du restaurateur lorsqu'aucun {@link #ownerId} existant n'est fourni.
     * Si {@code ownerEmail} ne correspond à aucun compte, un utilisateur
     * RESTAURANT_OWNER est créé ; {@code ownerSendInvite} déclenche l'email
     * d'activation tokenisé, sinon {@code ownerPassword} est utilisé directement.
     */
    private String ownerNom;
    private String ownerPrenom;
    private String ownerEmail;
    private String ownerTel;
    private String ownerPassword;
    private Boolean ownerSendInvite;
}
