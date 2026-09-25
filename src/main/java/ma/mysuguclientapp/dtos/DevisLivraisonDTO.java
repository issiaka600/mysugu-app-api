package ma.mysuguclientapp.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Devis (aperçu) des frais de livraison et de la remise automatique restaurant pour une
 * commande pas encore passée. Calculé avec exactement la même logique que
 * CommandeServiceImpl#createCommande (calculerFraisAvecZone / calculerRemisePromotion), pour
 * garantir que le total affiché au client dans le panier AVANT de valider correspond au total
 * réellement facturé APRÈS (correction PDF "Problème de montant total").
 */
@Data
@NoArgsConstructor
public class DevisLivraisonDTO {
    private BigDecimal sousTotal;
    private BigDecimal fraisLivraison;
    private BigDecimal montantTotal;
    private BigDecimal montantRemise;
    private BigDecimal montantFinal;
    private BigDecimal remisePromotion;

    public DevisLivraisonDTO(BigDecimal sousTotal, BigDecimal fraisLivraison,
                              BigDecimal montantRemise, BigDecimal montantFinal,
                              BigDecimal remisePromotion) {
        this.sousTotal = sousTotal;
        this.fraisLivraison = fraisLivraison;
        this.montantTotal = sousTotal.add(fraisLivraison);
        this.montantRemise = montantRemise;
        this.montantFinal = montantFinal;
        this.remisePromotion = remisePromotion;
    }
}
