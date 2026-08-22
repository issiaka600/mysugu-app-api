package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.TypeCommission;
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

    /**
     * Taux de commission prélevé par la plateforme sur les frais de livraison,
     * exprimé en fraction (0.15 = 15 %). Utilisé quand
     * {@link #commissionPlateformeType} vaut POURCENTAGE.
     */
    @Column(name = "taux_commission_plateforme", nullable = false, precision = 5, scale = 4)
    private BigDecimal tauxCommissionPlateforme = new BigDecimal("0.1500"); // 15%

    /**
     * Mode de calcul de la commission sur les frais de livraison.
     * Null = POURCENTAGE (comportement historique).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "commission_plateforme_type")
    private TypeCommission commissionPlateformeType;

    /**
     * Montant fixe prélevé par course quand {@link #commissionPlateformeType} vaut FIXE.
     * Il est plafonné aux frais de livraison : la plateforme ne peut pas prélever plus
     * que ce que le client a payé pour la course.
     */
    @Column(name = "commission_plateforme_montant_fixe", precision = 10, scale = 2)
    private BigDecimal commissionPlateformeMontantFixe;

    /**
     * Seuil de prix en dessous duquel la commission minimum globale s'applique (ex: 10 DH).
     * Pour les plats dont le prix ≤ seuilPrixCommission, on applique commissionMinPourcentage.
     * Pour les plats dont le prix > seuilPrixCommission, on applique le taux négocié du restaurant.
     */
    @Column(name = "seuil_prix_commission", precision = 8, scale = 2)
    private BigDecimal seuilPrixCommission = new BigDecimal("10.00");

    /**
     * Pourcentage de commission minimum global appliqué aux plats dont le prix ≤ seuilPrixCommission.
     * Exprimé en % (ex: 20 → 20%). Utilisé quand {@link #commissionMinType} vaut POURCENTAGE.
     */
    @Column(name = "commission_min_pourcentage", precision = 5, scale = 2)
    private BigDecimal commissionMinPourcentage = new BigDecimal("20.00");

    /**
     * Mode de calcul de la commission minimum globale.
     * Null = POURCENTAGE (comportement historique).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "commission_min_type")
    private TypeCommission commissionMinType;

    /**
     * Montant fixe prélevé par article sous le seuil quand {@link #commissionMinType}
     * vaut FIXE. C'est le cas d'usage principal des commissions fixes : sur un article
     * à bas prix, un pourcentage ne couvre pas le coût de traitement.
     */
    @Column(name = "commission_min_montant_fixe", precision = 10, scale = 2)
    private BigDecimal commissionMinMontantFixe;

    /** Périodicité de paiement des restaurants en mode PERIODIQUE (ex: 7 jours) */
    @Column(name = "periodicite_paiement_restaurant_jours", nullable = false)
    private Integer periodicitePaiementRestaurantJours = 7;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
