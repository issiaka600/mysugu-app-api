package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gains_livreur")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class GainsLivreur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livreur_id", nullable = false)
    private User livreur;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false, unique = true)
    private Commande commande;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    @Column(name = "frais_livraison", precision = 10, scale = 2)
    private BigDecimal fraisLivraison;

    @Column(name = "commission_plateforme", precision = 10, scale = 2)
    private BigDecimal commissionPlateforme;

    @Column(name = "montant_net", precision = 10, scale = 2)
    private BigDecimal montantNet;

    @Column(name = "est_paye")
    private Boolean estPaye = false;

    @Column(name = "paye_at")
    private LocalDateTime payeAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
