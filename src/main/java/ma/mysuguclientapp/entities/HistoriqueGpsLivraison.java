package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Point GPS persisté du trajet de livraison (analogue 6valley `delivery_histories`).
 *
 * MySugu ne persistait pas le trajet (tracking en mémoire via TrackingLocationStore).
 * Le shim expose POST record-location-data / GET last-location / GET order-delivery-history,
 * qui écrivent/lisent cette table. Voir techspec §9/§10.
 */
@Entity
@Table(name = "historique_gps_livraison",
        indexes = @Index(name = "idx_gps_commande", columnList = "commande_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class HistoriqueGpsLivraison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livreur_id")
    private User livreur;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "vitesse")
    private Double vitesse;

    @Column(name = "localisation")
    private String localisation;

    @Column(name = "point_at", nullable = false)
    private LocalDateTime pointAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
