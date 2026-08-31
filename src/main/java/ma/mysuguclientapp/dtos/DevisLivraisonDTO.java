package ma.mysuguclientapp.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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
@AllArgsConstructor
public class DevisLivraisonDTO {
    private BigDecimal fraisLivraison;
    private BigDecimal remisePromotion;
}
