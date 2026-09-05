package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Catégorie de plat configurable depuis le dashboard (au lieu de l'ancienne enum figée).
 * Le {@code code} est stable et referencé tel quel par {@code Plat.categoriePlat} ; le libellé,
 * l'ordre et l'icône sont libres et servis au client. */
@Entity
@Table(name = "categorie_plat_defs", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoriePlatDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code technique stable, repris tel quel dans Plat.categoriePlat (ex : "PLAT_PRINCIPAL"). */
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Column(nullable = false)
    private Integer ordre = 0;

    @Column(nullable = false)
    private Boolean actif = true;

    /** Emoji d'affichage (dashboard + suggestions client). Optionnel. */
    private String icone;
}