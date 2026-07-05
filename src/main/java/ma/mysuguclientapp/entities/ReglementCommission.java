package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.StatutReglementCommission;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Règlement périodique de commission livreur (analogue 6valley `commission_history`).
 *
 * Écran "commission" de l'app = cut plateforme informatif (15% des frais de livraison,
 * voir techspec §7). Pur bookkeeping : marquer une période payée insère une ligne ici,
 * SANS mutation de solde. Cycle : PENDING -> PAID (livreur) -> VALIDATED (admin).
 */
@Entity
@Table(name = "reglements_commission")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ReglementCommission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livreur_id", nullable = false)
    private User livreur;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutReglementCommission statut = StatutReglementCommission.PENDING;

    @Column(name = "transaction_ref")
    private String transactionRef;

    @Column(name = "paye_at")
    private LocalDateTime payeAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validateur_id")
    private User validateur;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
