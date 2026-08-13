package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.CategoriePlat;
import ma.mysuguclientapp.enumerations.ModeDisponibilitePlat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plats")
@Data
@NoArgsConstructor
@AllArgsConstructor
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
     * Disponibilité réellement présentée au client : le flag vendeur pondéré par le stock.
     * Le flag {@code isAvailable} n'est jamais écrasé en base — un réapprovisionnement
     * rend le produit visible sans réintervention du commerçant.
     */
    @Transient
    public boolean isEffectivementDisponible() {
        return Boolean.TRUE.equals(isAvailable)
                && (quantiteStock == null || quantiteStock > 0);
    }

    @Enumerated(EnumType.STRING)
    private CategoriePlat categoriePlat; // ENTREE, PLAT_PRINCIPAL, DESSERT, BOISSON

    /** Catégorie produit libre pour les verticales non-restaurant (alimentaire/cosmétique). */
    @Column(name = "categorie_produit")
    private String categorieProduit;

    @OneToMany(mappedBy = "plat", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<OptionGroup> optionGroups = new ArrayList<>();
}
