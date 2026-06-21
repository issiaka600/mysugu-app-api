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
