package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.commerce.PromotionCreateDTO;
import ma.mysuguclientapp.dtos.commerce.PromotionDTO;

import java.util.List;

public interface PromotionService {
    PromotionDTO creerPromotion(PromotionCreateDTO dto);
    List<PromotionDTO> getAllPromotions();
    PromotionDTO getPromotion(Long id);
    List<PromotionDTO> getPromotionsActives();
    List<PromotionDTO> getPromotionsRestaurant(Long restaurantId);
    PromotionDTO activerDesactiver(Long id, boolean actif);
    void supprimerPromotion(Long id);
    List<PromotionDTO> getPromotionsFlash();
}
