package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.ModeDisponibilitePlat;
import ma.mysuguclientapp.enumerations.TypeCommission;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plats", indexes = {
        @Index(name = "idx_plats_restaurant_rayon", columnList = "restaurant_id, categorie_produit")
})
@Data
@NoArgsConstructor
// Pas de @AllArgsConstructor : à vingt champs, l'appel positionnel compilait jusqu'au jour où
// l'on a ajouté un attribut, puis il fallait retrouver le rang du null manquant. Le seul
// appelant (TestDataInitializer) passe désormais par les setters.
public class Plat {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String nom;
    
    @Column(length = 1000)
    private String description;
    
    @Column(nullable = false)
    private BigDecimal prix;

    // ── Commission propre au plat ────────────────────────────────────────────
    // Les trois colonnes à null signifient « aucune commission spécifique » : c'est alors la
    // commission du restaurant qui s'applique. On ne matérialise donc pas un barème par défaut,
    // sinon un plat hériterait d'une copie figée de la commission du resto et ne suivrait plus
    // ses changement. Un barème explicite ici l'emporte sur tous les autres.
    /** Mode de calcul de la commission de ce plat ; null = hériter de la commission du restaurant. */
    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type")
    private TypeCommission commissionType;

    /**
     * Taux de commission de ce plat (en %), utilisé quand {@link #commissionType} vaut
     * POURCENTAGE. Null quand le plat n'a pas de commission propre, ou quand le barème est fixe.
     */
    @Column(name = "commission_pourcentage", precision = 5, scale = 2)
    private BigDecimal commissionPourcentage;

    /**
     * Montant prélevé par article quand {@link #commissionType} vaut FIXE. Null quand le plat
     * n'a pas de commission propre, ou quand le barème est un pourcentage.
     */
    @Column(name = "commission_montant_fixe", precision = 10, scale = 2)
    private BigDecimal commissionMontantFixe;

    /** Vrai si ce plat porte un barème qui lui est propre, plutôt que celui du restaurant. */
    public boolean aCommissionPropre() {
        return commissionType != null
                && (commissionPourcentage != null || commissionMontantFixe != null);
    }

    private String imageUrl;
    
    @ElementCollection
    @CollectionTable(name = "plat_ingredients", joinColumns = @JoinColumn(name = "plat_id"))
    @Column(name = "ingredient")
    private List<String> ingredients = new ArrayList<>();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;
    
    @Column(name = "is_available")
    private Boolean isAvailable = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_mode")
    private ModeDisponibilitePlat availabilityMode = ModeDisponibilitePlat.DISPONIBLE;

    @Column(name = "indisponible_jusqua")
    private LocalDateTime indisponibleJusqua;

    @Column(name = "temps_preparation")
    private Integer tempsPreparation; // en minutes

    /** Quantité en stock. Null = stock non géré (cas de tous les plats de restaurant). */
    @Column(name = "quantite_stock")
    private Integer quantiteStock;

    /** Seuil d'alerte stock bas, affiché au commerçant. Null = pas d'alerte. */
    @Column(name = "seuil_alerte_stock")
    private Integer seuilAlerteStock;

    /**
     * Sélection manuelle « Top des ventes » affichée dans l'appli client : seuls les plats
     * marqués à {@code true} depuis le dashboard apparaissent dans la rubrique Top des ventes.
     * Rien d'automatique à partir des ventes — c'est le commerçant qui choisit.
     */
    @Column(name = "top_vente")
    private Boolean topVente = false;

    /**
     * Disponibilité réellement présentée au client : le flag vendeur pondéré par le stock.
     * Le flag {@code isAvailable} n'est jamais écrasé en base — un réapprovisionnement
     * rend le produit visible sans réintervention du commerçant.
     */
    @Transient
    public boolean isEffectivementDisponible() {
        return Boolean.TRUE.equals(isAvailable)
                && (quantiteStock == null || quantiteStock > 0);
    }

    /** Code de catégorie de plat, stable, référencé dans {@code categorie_plat_defs}
     * (ex : "PLAT_PRINCIPAL", ou une catégorie personnalisée créée depuis le dashboard). */
    @Column(name = "categorie_plat")
    private String categoriePlat;

    /** Catégorie produit libre pour les verticales non-restaurant (alimentaire/cosmétique). */
    @Column(name = "categorie_produit")
    private String categorieProduit;

    @OneToMany(mappedBy = "plat", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<OptionGroup> optionGroups = new ArrayList<>();
}
