package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Représente la position de caisse (espèces) d'un livreur à un instant T.
 * soldeCourant = argent physiquement dans la poche du livreur
 *              = total collecté chez clients
 *              - total payé aux restaurants (mode PAR_COMMANDE)
 *              - total remis à la plateforme (réconciliations)
 *              + total des avances reçues de la plateforme
 */
@Entity
@Table(name = "caisses_livreur", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"livreur_id"})
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CaisseLivreur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livreur_id", nullable = false)
    private User livreur;

    /** Espèces actuellement dans la poche du livreur */
    @Column(name = "solde_courant", nullable = false, precision = 10, scale = 2)
    private BigDecimal soldeCourant = BigDecimal.ZERO;

    /** Cumul total encaissé chez les clients (espèces) depuis la dernière réconciliation */
    @Column(name = "total_collecte_session", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCollecteSession = BigDecimal.ZERO;

    /** Plafond personnalisé pour ce livreur (null = utilise le plafond global) */
    @Column(name = "plafond_personnalise", precision = 10, scale = 2)
    private BigDecimal plafondPersonnalise;

    /** Date/heure de la dernière réconciliation avec la plateforme */
    @Column(name = "derniere_reconciliation")
    private LocalDateTime derniereReconciliation;

    /** Alerte déjà envoyée pour le plafond atteint (évite le spam) */
    @Column(name = "alerte_plafond_envoyee")
    private Boolean alertePlafondEnvoyee = false;

    /** Alerte envoyée pour dépassement intervalle réconciliation */
    @Column(name = "alerte_intervalle_envoyee")
    private Boolean alerteIntervalleEnvoyee = false;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
