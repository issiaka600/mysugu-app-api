package ma.mysuguclientapp.dtos.caisse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Informations financières affichées au livreur avant de récupérer une commande cash.
 * Ce qu'il va encaisser et ce qu'il devra éventuellement payer au restaurant.
 */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InfoPaiementCommandeDTO {
    private Long commandeId;
    private String numeroCommande;
    private String restaurantNom;
    private String clientNom;
    private String adresseLivraison;

    /** Montant nourriture */
    private BigDecimal montantCommande;

    /** Frais de livraison */
    private BigDecimal fraisLivraison;

    /** Total à encaisser chez le client */
    private BigDecimal totalAEncaisserClient;

    /** Mode de paiement restaurant */
    private String modePaiementRestaurant;

    /**
     * true si le livreur doit payer le restaurant à la récupération.
     * (mode PAR_COMMANDE uniquement)
     */
    private boolean doitPayerRestaurant;

    /** Montant à payer au restaurant (mode PAR_COMMANDE) */
    private BigDecimal montantAPayerRestaurant;

    /** Solde courant de la caisse du livreur */
    private BigDecimal soldeCaisseLivreur;

    /** true si le livreur a suffisamment de liquidités pour payer le restaurant */
    private boolean liquiditesSuffisantes;

    /** Gain net du livreur pour cette course */
    private BigDecimal gainNetLivreur;
}
