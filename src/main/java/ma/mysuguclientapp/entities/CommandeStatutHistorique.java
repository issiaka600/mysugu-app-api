package ma.mysuguclientapp.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Étape visible dans le suivi Customer d'une commande.
 * Les statuts internes (notamment ASSIGNEE_LIVREUR) ne sont pas enregistrés ici.
 */
@Entity
@Table(name = "commande_statut_historiques",
        indexes = @Index(name = "idx_commande_statut_history", columnList = "commande_id,changed_at"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommandeStatutHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    /** Statut normalisé pour les applications mobiles : pending, confirmed, etc. */
    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
