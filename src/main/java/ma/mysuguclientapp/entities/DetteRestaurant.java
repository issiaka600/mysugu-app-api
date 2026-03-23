package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.StatutDetteRestaurant;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Représente ce que la plateforme doit à un restaurant pour une commande cash.
 * Créée automatiquement quand une commande espèces est livrée.
 */
@Entity
@Table(name = "dettes_restaurant", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"commande_id"})
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DetteRestaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    /** Montant dû au restaurant = montantTotal de la commande (hors frais de livraison) */
    @Column(name = "montant_du", nullable = false, precision = 10, scale = 2)
    private BigDecimal montantDu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDetteRestaurant statut = StatutDetteRestaurant.EN_ATTENTE;

    /**
     * Si statut = PAYE_PAR_LIVREUR, référence vers le livreur qui a payé.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paye_par_livreur_id")
    private User payeParLivreur;

    /** Lié au paiement groupé si mode PERIODIQUE */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paiement_restaurant_id")
    private PaiementRestaurant paiementRestaurant;

    @Column(name = "date_paiement")
    private LocalDateTime datePaiement;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
