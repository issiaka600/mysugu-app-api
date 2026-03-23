package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.CategorieRestaurantDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CategorieRestaurantService {
    List<CategorieRestaurantDTO> getAllCategories();
    CategorieRestaurantDTO getCategorieById(Long id);
    CategorieRestaurantDTO createCategorie(String nom, String description, MultipartFile image);
    CategorieRestaurantDTO updateCategorie(Long id, String nom, String description, MultipartFile image);
    void deleteCategorie(Long id);
    List<RestaurantDTO> getRestaurantsByCategorie(Long id);
}
