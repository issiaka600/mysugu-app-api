package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.TypeCommission;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lignes_commande")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LigneCommande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plat_id", nullable = false)
    private Plat plat;

    @Column(nullable = false)
    private Integer quantite;

    @Column(nullable = false)
    private BigDecimal prixUnitaire;

    @Column(nullable = false)
    private BigDecimal montantTotal;

    @Column(length = 500)
    private String remarque; // Instructions spéciales pour ce plat

    /** Quantité réellement livrée (renseignée par le vendeur/livreur), différente de "quantite" en cas d'écart */
    @Column(name = "quantite_livree")
    private Integer quantiteLivree;

    /**
     * Mode de calcul de la commission appliquée à cette ligne, capturé au moment de la commande.
     * Null = POURCENTAGE, pour les lignes antérieures aux commissions fixes.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type")
    private TypeCommission commissionType;

    /**
     * Taux de commission appliqué à cette ligne (en %), capturé au moment de la commande.
     * Null quand la commission est un montant fixe.
     */
    @Column(name = "commission_pourcentage", precision = 5, scale = 2)
    private BigDecimal commissionPourcentage;

    /**
     * Montant fixe unitaire appliqué à cette ligne, capturé au moment de la commande.
     * Null quand la commission est un pourcentage.
     */
    @Column(name = "commission_montant_fixe", precision = 10, scale = 2)
    private BigDecimal commissionMontantFixe;

    /**
     * Montant de commission calculé pour cette ligne : {@code montantTotal × pourcentage / 100}
     * en mode POURCENTAGE, {@code montantFixe × quantite} en mode FIXE.
     */
    @Column(name = "montant_commission", precision = 10, scale = 2)
    private BigDecimal montantCommission = BigDecimal.ZERO;

    @OneToMany(mappedBy = "ligneCommande", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneCommandeOption> options = new ArrayList<>();
}