package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.TypeTransactionPoints;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions_points")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionPoints {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "points_fidelite_id", nullable = false)
    private PointsFidelite pointsFidelite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeTransactionPoints type;

    @Column(nullable = false)
    private Integer points;

    private String description;

    @Column(name = "reference_commande")
    private String referenceCommande;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
