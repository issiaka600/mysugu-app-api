package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.FiltreComportement;
import ma.mysuguclientapp.enumerations.FiltreContexte;

@Entity
@Table(name = "filtres")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Filtre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Barre concernée (verticale). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FiltreContexte contexte;

    /** Comportement connu, interprété côté app. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FiltreComportement comportement;

    /** Catégorie ciblée lorsque comportement = CATEGORIE. */
    @Column(name = "categorie_id")
    private Long categorieId;

    @Column(nullable = false)
    private String libelle;

    /** Clé d'icône Lucide (ex: "Star"). */
    private String icone;

    @Column(nullable = false)
    private Integer ordre = 0;

    @Column(nullable = false)
    private Boolean actif = true;
}
