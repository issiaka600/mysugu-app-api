package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Snapshot figé d'une option choisie sur une ligne de commande. */
@Entity
@Table(name = "ligne_commande_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LigneCommandeOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ligne_commande_id", nullable = false)
    private LigneCommande ligneCommande;

    @Column(name = "option_item_id")
    private Long optionItemId;

    @Column(name = "option_group_nom")
    private String optionGroupNom;

    @Column(name = "option_nom")
    private String optionNom;

    @Column(name = "prix_supplement", precision = 10, scale = 2)
    private BigDecimal prixSupplement;
}
