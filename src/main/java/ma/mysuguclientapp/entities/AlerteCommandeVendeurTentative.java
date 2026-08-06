package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Trace d'un envoi FCM afin de distinguer l'acceptation par Firebase de la réception par le mobile. */
@Entity
@Table(name = "alertes_commande_vendeur_tentatives",
        indexes = @Index(name = "idx_alerte_vendeur_tentative", columnList = "alerte_id,created_at"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlerteCommandeVendeurTentative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alerte_id", nullable = false)
    private AlerteCommandeVendeur alerte;

    @Column(name = "tokens_attempted", nullable = false)
    private Integer tokensAttempted;

    @Column(name = "tokens_sent", nullable = false)
    private Integer tokensSent;

    @Column(name = "firebase_message_ids", length = 2000)
    private String firebaseMessageIds;

    @Column(name = "error_summary", length = 2000)
    private String errorSummary;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
