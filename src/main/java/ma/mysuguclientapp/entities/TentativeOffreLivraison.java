package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Résultat technique FCM de la proposition de livraison envoyée à un livreur. */
@Entity
@Table(name = "tentatives_offre_livraison",
        indexes = @Index(name = "idx_tentative_offre_livraison", columnList = "offre_id,created_at"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TentativeOffreLivraison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offre_id", nullable = false)
    private OffreLivraison offre;

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
