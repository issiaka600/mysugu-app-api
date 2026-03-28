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
    private StatutCommande statut; // EN_ATTENTE, CONFIRMEE, EN_PREPARATION, EN_COURS, LIVREE, ANNULEE
    
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

    @Column(name = "is_reorder")
    private Boolean isReorder = false;

    @Column(name = "reorder_from_id")
    private Long reorderFromId;

    @PrePersist
    @PreUpdate
    public void applyDefaults() {
        if (modeReception == null) {
            modeReception = ModeReceptionCommande.LIVRAISON;
        }
    }
}
