package ma.mysuguclientapp.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommandeDTO {
    private Long id;
    private String numeroCommande;
    private UserDTO client;
    private RestaurantDTO restaurant;
    private UserDTO livreur;
    private List<LigneCommandeDTO> lignesCommande;
    private String statut;
    /** Alias mobile normalisé de {@link #statut}. */
    @JsonProperty("status")
    public String getStatus() {
        if (statut == null) return null;
        return switch (statut) {
            case "EN_ATTENTE", "NON_FINALISEE" -> "pending";
            case "CONFIRMEE" -> "confirmed";
            case "EN_PREPARATION" -> "processing";
            case "PRETE" -> "ready";
            case "ASSIGNEE_LIVREUR", "EN_COURS" -> "out_for_delivery";
            case "LIVREE" -> "delivered";
            case "ANNULEE" -> "canceled";
            case "RETOURNEE" -> "returned";
            case "ECHEC_LIVRAISON" -> "failed";
            default -> statut.toLowerCase();
        };
    }
    private String trackingStatut;
    /** Historique chronologique du suivi Customer, avec statuts normalisés pour mobile. */
    private List<CommandeStatusHistoryDTO> statusHistory;
    private LocalisationDTO adresseLivraison;
    private BigDecimal montantTotal;
    /** Somme des montants des lignes d'article, hors frais de livraison et hors remises.
     *  MontantTotal = montantArticles + fraisLivraison. */
    private BigDecimal montantArticles;
    private BigDecimal montantRemise;
    private BigDecimal montantFinal;
    private String codePromoUtilise;
    private BigDecimal fraisLivraison;
    private String currency = "MAD";
    private String currencySymbol = "DH";
    private Integer tempsLivraisonEstime;
    private BigDecimal montantCommissionTotal;
    /** Part nette du montant final revenant au vendeur, calculée côté serveur. */
    private BigDecimal montantVendeur;
    private String commentaire;
    private String raisonAnnulation;
    @JsonProperty("canceled_by")
    private String canceledBy;
    @JsonProperty("cancellation_reason")
    private String cancellationReason;
    @JsonProperty("canceled_at")
    private LocalDateTime canceledAt;
    private String modeReception;
    private String methodePaiement;
    private String statutPaiement;
    private String stripeClientSecret;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime livreeAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime dateLivraisonPrevue;
    private String causeReport;
    private String livreurTiersNom;
    private String livreurTiersTelephone;
    private String livreurTiersEntreprise;
}
