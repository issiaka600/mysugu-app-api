package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.ModePaiementRestaurant;
import ma.mysuguclientapp.enumerations.ModeVersementRestaurant;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Préférences de paiement d'un restaurant.
 * Créé à la demande (ou lors de l'onboarding du restaurant).
 */
@Entity
@Table(name = "parametres_paiement_restaurant")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ParametresPaiementRestaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false, unique = true)
    private Restaurant restaurant;

    /**
     * PERIODIQUE : platform paie le restaurant tous les N jours.
     * PAR_COMMANDE : livreur paie le restaurant à la récupération du plat.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", nullable = false)
    private ModePaiementRestaurant modePaiement = ModePaiementRestaurant.PERIODIQUE;

    /** Période en jours pour le mode PERIODIQUE (null si PAR_COMMANDE) */
    @Column(name = "periodicite_jours")
    private Integer periodicitéJours;

    /** Mode de versement pour le mode PERIODIQUE */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_versement")
    private ModeVersementRestaurant modeVersement = ModeVersementRestaurant.VIREMENT_BANCAIRE;

    /** RIB/IBAN pour virement bancaire */
    @Column(name = "rib", length = 34)
    private String rib;

    /** Nom du bénéficiaire pour virement */
    @Column(name = "nom_beneficiaire")
    private String nomBeneficiaire;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
