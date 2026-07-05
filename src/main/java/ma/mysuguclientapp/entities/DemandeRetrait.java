package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.StatutRetrait;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Demande de retrait bancaire d'un livreur (analogue 6valley `withdraw_requests`).
 *
 * Modèle argent (voir TIKTAK_LIVREUR_MIGRATION_TECHSPEC.md §7) :
 * - création : statut EN_ATTENTE, contribue à `pending_withdraw` (pas de mutation de solde).
 * - approbation admin : APPROUVE -> contribue à `total_withdraw`, et marque les GainsLivreur
 *   correspondants estPaye=true (c'est ce qui réduit `current_balance`).
 * - refus : REFUSE -> ne contribue plus à pending, les gains restent non payés.
 */
@Entity
@Table(name = "demandes_retrait")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DemandeRetrait {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livreur_id", nullable = false)
    private User livreur;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    @Column(name = "note")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutRetrait statut = StatutRetrait.EN_ATTENTE;

    @Column(name = "transaction_ref")
    private String transactionRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_validateur_id")
    private User adminValidateur;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
