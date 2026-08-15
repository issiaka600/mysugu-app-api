package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.Vertical;

/** Rayon d'une boutique (verticale non-restaurant). Le code est stable, le libellé est affiché. */
@Entity
@Table(name = "categories_produit",
        uniqueConstraints = @UniqueConstraint(columnNames = {"vertical", "code"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategorieProduit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Vertical vertical;

    /** Code technique stable, repris tel quel dans Plat.categorieProduit (ex : "fruits_legumes"). */
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Column(nullable = false)
    private Integer ordre = 0;

    @Column(nullable = false)
    private Boolean actif = true;
}
