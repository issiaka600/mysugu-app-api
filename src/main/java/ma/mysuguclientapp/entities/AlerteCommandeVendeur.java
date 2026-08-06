package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.StatutAlerteCommandeVendeur;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Campagne d'alerte déclenchée à la création d'une commande.
 * Une commande ne peut avoir qu'une seule alerte vendeur : cela rend le déclenchement idempotent.
 */
@Entity
@Table(name = "alertes_commande_vendeur",
        indexes = @Index(name = "idx_alerte_vendeur_due", columnList = "statut,next_attempt_at"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlerteCommandeVendeur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false, unique = true)
    private Commande commande;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendeur_id", nullable = false)
    private User vendeur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutAlerteCommandeVendeur statut;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "stopped_at")
    private LocalDateTime stoppedAt;

    @Column(name = "stop_reason", length = 100)
    private String stopReason;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
