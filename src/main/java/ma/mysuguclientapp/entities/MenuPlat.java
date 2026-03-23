package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "menu_plats", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"menu_id", "plat_id"})
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuPlat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plat_id", nullable = false)
    private Plat plat;

    @Column(name = "prix_special", precision = 10, scale = 2)
    private BigDecimal prixSpecial; // Override price for this menu, nullable

    @Column(name = "ordre_affichage")
    private Integer ordreAffichage = 0;
}
