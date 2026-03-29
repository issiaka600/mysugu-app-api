package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

    /** Taux de commission appliqué à cette ligne (en %), capturé au moment de la commande */
    @Column(name = "commission_pourcentage", precision = 5, scale = 2)
    private BigDecimal commissionPourcentage;

    /** Montant de commission calculé pour cette ligne (prixUnitaire × quantite × commissionPourcentage / 100) */
    @Column(name = "montant_commission", precision = 10, scale = 2)
    private BigDecimal montantCommission = BigDecimal.ZERO;
}
