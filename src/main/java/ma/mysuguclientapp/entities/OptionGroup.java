package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.OptionSelectionMode;

import java.util.ArrayList;
import java.util.List;

/** Section d'options d'un plat (ex: « Accompagnement », « Suppléments », « Sauces »). */
@Entity
@Table(name = "plat_option_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plat_id", nullable = false)
    private Plat plat;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_mode", nullable = false)
    private OptionSelectionMode selectionMode = OptionSelectionMode.SINGLE;

    @Column(nullable = false)
    private Boolean obligatoire = false;

    @Column(name = "min_selections", nullable = false)
    private Integer minSelections = 0;

    /** Null = illimité (sauf SINGLE où max = 1). */
    @Column(name = "max_selections")
    private Integer maxSelections;

    @Column(nullable = false)
    private Integer ordre = 0;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<OptionItem> items = new ArrayList<>();
}
