package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "panier_items")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PanierItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "panier_id", nullable = false)
    private Panier panier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plat_id", nullable = false)
    private Plat plat;

    @Column(nullable = false)
    private Integer quantite;

    @Column(name = "prix_unitaire", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixUnitaire;

    @Column(length = 500)
    private String remarque;

    @Builder.Default
    @OneToMany(mappedBy = "panierItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PanierItemOption> options = new ArrayList<>();
}
