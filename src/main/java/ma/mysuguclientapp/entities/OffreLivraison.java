package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.StatutOffreLivraison;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Offre temporaire envoyée à un seul livreur ; les offres suivantes restent bloquées jusque-là. */
@Entity
@Table(name = "offres_livraison", indexes = {
        @Index(name = "idx_offre_livraison_expiry", columnList = "statut,expires_at"),
        @Index(name = "idx_offre_livraison_livreur", columnList = "livreur_id,statut")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffreLivraison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livreur_id", nullable = false)
    private User livreur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutOffreLivraison statut;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "distance_km", nullable = false)
    private Double distanceKm;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "next_alert_at", nullable = false)
    private LocalDateTime nextAlertAt;

    @Column(name = "last_alert_at")
    private LocalDateTime lastAlertAt;

    @Column(name = "alert_attempt_count", nullable = false)
    private Integer alertAttemptCount = 0;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
