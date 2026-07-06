package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.util.List;

/**
 * Version simplifiée de "order-wise-product-upload" (legacy TikTak, feature POS).
 * Permet au vendeur/livreur de déclarer, ligne par ligne, la quantité réellement
 * livrée si elle diffère de la quantité commandée (rupture de stock partielle, etc.).
 */
@Data
public class OrderWiseProductUploadDTO {
    private List<LigneLivreeDTO> lignes;

    @Data
    public static class LigneLivreeDTO {
        private Long ligneCommandeId;
        private Integer quantiteLivree;
    }
}