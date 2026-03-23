package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Paramètres globaux de la caisse, configurables par l'admin.
 * Un seul enregistrement (id = 1).
 */
@Entity
@Table(name = "parametres_caisse")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ParametresCaisse {

    @Id
    private Long id = 1L; // Singleton

    /** Plafond de liquidités espèces qu'un livreur peut détenir (ex: 500 MAD) */
    @Column(name = "plafond_caisse_livreur", nullable = false, precision = 10, scale = 2)
    private BigDecimal plafondCaisseLivreur = new BigDecimal("500.00");

    /**
     * Seuil d'alerte en % du plafond (ex: 80 → alerte à 400 MAD si plafond = 500).
     * Déclenche une notification pour que le livreur vienne faire le point.
     */
    @Column(name = "seuil_alerte_pourcentage", nullable = false)
    private Integer seuilAlertePourcentage = 80;

    /**
     * Intervalle de réconciliation obligatoire en heures (ex: 48h).
     * Si un livreur dépasse cet intervalle sans réconciliation, alerte admin.
     */
    @Column(name = "intervalle_reconciliation_heures", nullable = false)
    private Integer intervalleReconciliationHeures = 48;

    /** Taux de commission prélevé par la plateforme sur les frais de livraison */
    @Column(name = "taux_commission_plateforme", nullable = false, precision = 5, scale = 4)
    private BigDecimal tauxCommissionPlateforme = new BigDecimal("0.1500"); // 15%

    /** Périodicité de paiement des restaurants en mode PERIODIQUE (ex: 7 jours) */
    @Column(name = "periodicite_paiement_restaurant_jours", nullable = false)
    private Integer periodicitePaiementRestaurantJours = 7;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
