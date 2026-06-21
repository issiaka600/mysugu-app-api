package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Snapshot figé d'une option choisie sur une ligne de panier. */
@Entity
@Table(name = "panier_item_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PanierItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "panier_item_id", nullable = false)
    private PanierItem panierItem;

    @Column(name = "option_item_id")
    private Long optionItemId;

    @Column(name = "option_group_nom")
    private String optionGroupNom;

    @Column(name = "option_nom")
    private String optionNom;

    @Column(name = "prix_supplement", precision = 10, scale = 2)
    private BigDecimal prixSupplement;
}
