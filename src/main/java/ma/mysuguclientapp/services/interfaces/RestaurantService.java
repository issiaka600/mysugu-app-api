package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.RestaurantCreateDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RestaurantService {
    /**
     * Obtenir tous les restaurants avec filtres et pagination
     */
    Page<RestaurantDTO> getAllRestaurants(Long categorieId, Double latitude, Double longitude,
                                          Double maxDistance, String vertical, Pageable pageable);

    /**
     * Obtenir un restaurant par ID
     */
    RestaurantDTO getRestaurantById(Long id);

    /**
     * Rechercher des restaurants par mot-clé
     */
    List<RestaurantDTO> searchRestaurants(String keyword);

    /**
     * Obtenir les restaurants les mieux notés
     */
    List<RestaurantDTO> getTopRatedRestaurants(int limit);

    /**
     * Obtenir les restaurants à proximité
     */
    List<RestaurantDTO> getNearbyRestaurants(Double latitude, Double longitude, Double radiusKm);

    /**
     * Créer un nouveau restaurant
     */
    RestaurantDTO createRestaurant(RestaurantCreateDTO restaurantDTO, MultipartFile logo);

    /**
     * Mettre à jour un restaurant
     */
    RestaurantDTO updateRestaurant(Long id, RestaurantCreateDTO restaurantDTO, MultipartFile logo);

    /**
     * Supprimer un restaurant
     */
    void deleteRestaurant(Long id);

    /**
     * Activer/désactiver un restaurant
     */
    RestaurantDTO toggleRestaurantStatus(Long id);

    /**
     * Définir le taux de commission négocié avec un restaurant (admin)
     */
    RestaurantDTO setCommissionPourcentage(Long id, java.math.BigDecimal pourcentage);

}
