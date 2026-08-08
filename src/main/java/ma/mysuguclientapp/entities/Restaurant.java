package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.StatutRestaurant;
import ma.mysuguclientapp.enumerations.Vertical;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "restaurants")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Restaurant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String nom;
    
    @Column(length = 1000)
    private String description;
    
    private String logoUrl;

    /** Object name MinIO de la bannière de la boutique. */
    private String bannerUrl;
    
    @Column(name = "appreciation")
    private Double appreciation = 0.0; // Note moyenne (0-5)
    
    @Column(name = "nombre_avis")
    private Integer nombreAvis = 0;
    
    @Column(name = "temps_livraison_moyen")
    private Integer tempsLivraisonMoyen; // en minutes
    
    @Embedded
    private Localisation localisation;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private CategorieRestaurant categorie;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    /** Verticale de service de ce commerçant (RESTAURANT par défaut). Null = RESTAURANT. */
    @Enumerated(EnumType.STRING)
    @Column(name = "vertical")
    private Vertical vertical;

    /**
     * Zone de déploiement à laquelle appartient ce restaurant.
     * Détermine la couverture géographique du service de livraison.
     * Null = aucune restriction de zone appliquée.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_deploiement_id")
    private ZoneDeploiement zoneDeploiement;
    
    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Plat> plats = new ArrayList<>();
    
    @Column(name = "is_active")
    private Boolean isActive = true;

    /**
     * Statut d'approbation dans le workflow d'onboarding restaurateur.
     * Défaut {@link StatutRestaurant#APPROUVE} : les restaurants créés par l'admin
     * (et les anciennes données) sont considérés approuvés. L'onboarding restaurateur
     * positionne explicitement {@link StatutRestaurant#EN_ATTENTE}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_approbation")
    private StatutRestaurant statutApprobation = StatutRestaurant.APPROUVE;

    /** Motif de rejet ou détail de la demande de complément formulée par l'admin. */
    @Column(name = "motif_revue", length = 1000)
    private String motifRevue;

    /** Date de la dernière décision de revue (approbation / rejet / demande de complément). */
    @Column(name = "date_revue")
    private LocalDateTime dateRevue;

    /** Identifiant de l'admin ayant statué sur la demande. */
    @Column(name = "revue_par")
    private Long revuePar;

    /** Object names (MinIO) des justificatifs fournis par le restaurateur. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "restaurant_justificatifs",
            joinColumns = @JoinColumn(name = "restaurant_id"))
    @Column(name = "object_name", length = 500)
    private List<String> justificatifs = new ArrayList<>();

    @Column(name = "auto_close_enabled")
    private Boolean autoCloseEnabled = false;

    @Column(name = "heure_ouverture")
    private LocalTime heureOuverture;

    @Column(name = "heure_fermeture")
    private LocalTime heureFermeture;

    @Column(name = "horaires_ouverture", length = 500)
    private String horairesOuverture; // Format JSON ou texte

    /**
     * Pourcentage de commission négocié avec ce restaurant (en %).
     * S'applique aux plats dont le prix dépasse le seuilPrixCommission global.
     * Ex: 15 → 15%.
     */
    @Column(name = "commission_pourcentage", precision = 5, scale = 2)
    private BigDecimal commissionPourcentage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
