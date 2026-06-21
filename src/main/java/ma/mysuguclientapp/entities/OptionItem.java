package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Choix au sein d'une section d'options (ex: « Frites », « Fromage »). */
@Entity
@Table(name = "plat_option_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private OptionGroup group;

    @Column(nullable = false)
    private String nom;

    /** Supplément de prix ; 0 = inclus/gratuit. */
    @Column(name = "prix_supplement", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixSupplement = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean disponible = true;

    @Column(nullable = false)
    private Integer ordre = 0;
}
