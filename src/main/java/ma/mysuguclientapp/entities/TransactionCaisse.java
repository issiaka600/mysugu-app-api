package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.TypeTransactionCaisse;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Journal de toutes les mouvements d'espèces impliquant un livreur.
 */
@Entity
@Table(name = "transactions_caisse")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionCaisse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caisse_livreur_id", nullable = false)
    private CaisseLivreur caisseLivreur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id")
    private Commande commande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeTransactionCaisse type;

    /** Montant du mouvement (toujours positif ; le sens est donné par le type) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    /** Solde de la caisse avant ce mouvement */
    @Column(name = "solde_avant", precision = 10, scale = 2)
    private BigDecimal soldeAvant;

    /** Solde de la caisse après ce mouvement */
    @Column(name = "solde_apres", precision = 10, scale = 2)
    private BigDecimal soldeApres;

    private String description;

    /** Admin qui a validé (pour REMISE_PLATEFORME, AJUSTEMENT_ADMIN) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirme_par_id")
    private User confirmepar;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
