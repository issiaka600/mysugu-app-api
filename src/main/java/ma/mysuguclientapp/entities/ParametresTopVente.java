package ma.mysuguclientapp.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Paramètres globaux de la rubrique « Top des ventes », configurables par l'admin.
 * Un seul enregistrement (id = 1).
 *
 * <p>Un plat devient automatiquement « Top des ventes » dès que le nombre de ventes
 * cumulé (quantités vendues, commandes LIVREES uniquement) atteint {@link #seuilVentes}.
 * Le flag manuel {@code Plat.topVente} (choisi par le commerçant) s'additionne à ce
 * calcul automatique : il n'est jamais écrasé.</p>
 */
@Entity
@Table(name = "parametres_top_vente")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParametresTopVente {

    @Id
    private Long id = 1L; // Singleton

    /**
     * Nombre de ventes à partir duquel un plat devient automatiquement « Top des ventes »
     * (ex : 5 → tout plat vendu 5 fois et plus est mis en avant, par restaurant).
     */
    @Column(name = "seuil_ventes", nullable = false)
    private Integer seuilVentes = 5;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}