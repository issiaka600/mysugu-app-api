package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.ModeReceptionCommande;
import ma.mysuguclientapp.enumerations.MethodePaiement;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "commandes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String numeroCommande;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livreur_id")
    private User livreur;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneCommande> lignesCommande = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCommande statut; // EN_ATTENTE, CONFIRMEE, EN_PREPARATION, PRETE, ASSIGNEE_LIVREUR, EN_COURS, LIVREE, ANNULEE

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "livraison_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "livraison_longitude")),
            @AttributeOverride(name = "adresse", column = @Column(name = "livraison_adresse")),
            @AttributeOverride(name = "ville", column = @Column(name = "livraison_ville")),
            @AttributeOverride(name = "codePostal", column = @Column(name = "livraison_code_postal")),
            @AttributeOverride(name = "pays", column = @Column(name = "livraison_pays"))
    })
    private Localisation adresseLivraison;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_reception")
    private ModeReceptionCommande modeReception = ModeReceptionCommande.LIVRAISON;

    @Column(nullable = false)
    private BigDecimal montantTotal;

    @Column(name = "montant_remise")
    private BigDecimal montantRemise = BigDecimal.ZERO;

    @Column(name = "montant_final")
    private BigDecimal montantFinal;

    @Column(name = "code_promo_utilise", length = 50)
    private String codePromoUtilise;

    private BigDecimal fraisLivraison;

    @Column(name = "temps_livraison_estime")
    private Integer tempsLivraisonEstime; // en minutes

    @Column(length = 500)
    private String commentaire;

    @Column(name = "raison_annulation", length = 1000)
    private String raisonAnnulation;

    @Column(name = "canceled_by", length = 30)
    private String canceledBy;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    /** Marque que le stock des lignes a déjà été re-crédité. Empêche une double restitution. */
    @Column(name = "stock_restitue")
    private Boolean stockRestitue = false;

    @Enumerated(EnumType.STRING)
    private MethodePaiement methodePaiement; // CARTE, ESPECES, MOBILE_MONEY

    @Enumerated(EnumType.STRING)
    private StatutPaiement statutPaiement; // EN_ATTENTE, PAYE, REMBOURSE

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "livree_at")
    private LocalDateTime livreeAt;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /** Total de commission plateforme sur les articles de cette commande */
    @Column(name = "montant_commission_total", precision = 10, scale = 2)
    private BigDecimal montantCommissionTotal = BigDecimal.ZERO;

    @Column(name = "is_reorder")
    private Boolean isReorder = false;

    @Column(name = "reorder_from_id")
    private Long reorderFromId;

    @Column(name = "tiktak_order_id", unique = true)
    private Long tiktakOrderId;

    @Column(name = "tiktak_sync_status", length = 50)
    private String tiktakSyncStatus;

    @Column(name = "stripe_payment_intent_id", length = 100)
    private String stripePaymentIntentId;

    // --- Legacy livreur (shim Tiktak, techspec §5/§6) ---

    /** Code OTP de vérification de livraison (6valley `orders.verification_code`). */
    @Column(name = "code_verification_livraison", length = 10)
    private String codeVerificationLivraison;

    /** Livraison vérifiée par OTP (6valley `orders.verification_status`). */
    @Column(name = "livraison_verifiee")
    private Boolean livraisonVerifiee = false;

    /** Commande mise en pause par le livreur (6valley `orders.is_pause`) + cause. */
    @Column(name = "en_pause")
    private Boolean enPause = false;

    @Column(name = "cause_pause", length = 1000)
    private String causePause;

    /** Date de livraison prévue rééchelonnée (6valley `orders.expected_delivery_date`) + cause. */
    @Column(name = "date_livraison_prevue")
    private LocalDateTime dateLivraisonPrevue;

    @Column(name = "cause_report", length = 1000)
    private String causeReport;

    // ===== Livraison par un tiers (hors plateforme, ex: coursier externe) =====
    @Column(name = "livreur_tiers_nom", length = 150)
    private String livreurTiersNom;

    @Column(name = "livreur_tiers_telephone", length = 30)
    private String livreurTiersTelephone;

    @Column(name = "livreur_tiers_entreprise", length = 150)
    private String livreurTiersEntreprise;

    @PrePersist
    @PreUpdate
    public void applyDefaults() {
        if (modeReception == null) {
            modeReception = ModeReceptionCommande.LIVRAISON;
        }
    }
}
