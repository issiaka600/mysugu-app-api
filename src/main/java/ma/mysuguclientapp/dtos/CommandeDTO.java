package ma.mysuguclientapp.dtos;

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
    private String trackingStatut;
    private LocalisationDTO adresseLivraison;
    private BigDecimal montantTotal;
    private BigDecimal montantRemise;
    private BigDecimal montantFinal;
    private String codePromoUtilise;
    private BigDecimal fraisLivraison;
    private String currency = "MAD";
    private String currencySymbol = "DH";
    private Integer tempsLivraisonEstime;
    private BigDecimal montantCommissionTotal;
    private String commentaire;
    private String raisonAnnulation;
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
