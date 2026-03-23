package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.ModeVersementRestaurant;
import ma.mysuguclientapp.enumerations.StatutPaiementRestaurant;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Paiement effectué par la plateforme à un restaurant.
 * Couvre une ou plusieurs commandes (dettes).
 */
@Entity
@Table(name = "paiements_restaurant")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PaiementRestaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "montant_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montantTotal;

    /** Début de la période couverte */
    @Column(name = "periode_debut")
    private LocalDateTime periodeDebut;

    /** Fin de la période couverte */
    @Column(name = "periode_fin")
    private LocalDateTime periodeFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_versement", nullable = false)
    private ModeVersementRestaurant modeVersement;

    /** Référence du virement bancaire ou numéro de reçu cash */
    @Column(name = "reference", length = 100)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutPaiementRestaurant statut = StatutPaiementRestaurant.EN_COURS;

    /** Admin qui a effectué le paiement */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "effectue_par_id")
    private User effectuePar;

    @Column(name = "note", length = 500)
    private String note;

    @OneToMany(mappedBy = "paiementRestaurant")
    @Builder.Default
    private List<DetteRestaurant> dettes = new ArrayList<>();

    @Column(name = "date_paiement")
    private LocalDateTime datePaiement;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
