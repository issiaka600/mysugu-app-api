package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.CategorieRestaurantDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CategorieRestaurantService {
    /** @param vertical filtre optionnel : absent ⇒ RESTAURANT, "ALL" ⇒ toutes verticales, valeur inconnue ⇒ 400. */
    List<CategorieRestaurantDTO> getAllCategories(String vertical);
    CategorieRestaurantDTO getCategorieById(Long id);
    /** @param vertical optionnel : absent/vide ⇒ catégorie de restaurant (comportement historique), inconnu ⇒ 400. */
    CategorieRestaurantDTO createCategorie(
            String nom,
            String description,
            String vertical,
            MultipartFile image,
            MultipartFile imageTop,
            MultipartFile imageBanner);
    /** @param vertical optionnel : absent/vide ⇒ inchangée, inconnu ⇒ 400. */
    CategorieRestaurantDTO updateCategorie(
            Long id,
            String nom,
            String description,
            String vertical,
            MultipartFile image,
            MultipartFile imageTop,
            MultipartFile imageBanner);
    void deleteCategorie(Long id);
    List<RestaurantDTO> getRestaurantsByCategorie(Long id);
}
