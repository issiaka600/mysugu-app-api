package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.TypeReduction;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "codes_promo")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CodePromo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_reduction", nullable = false)
    private TypeReduction typeReduction;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valeur;

    @Column(name = "montant_min_commande", precision = 10, scale = 2)
    private BigDecimal montantMinCommande;

    @Column(name = "montant_max_reduction", precision = 10, scale = 2)
    private BigDecimal montantMaxReduction;

    @Column(name = "date_debut")
    private LocalDateTime dateDebut;

    @Column(name = "date_fin")
    private LocalDateTime dateFin;

    @Column(name = "usage_max")
    private Integer usageMax;

    @Column(name = "usage_count")
    private Integer usageCount = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
