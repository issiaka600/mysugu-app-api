package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.PlatAvailabilityUpdateDTO;
import ma.mysuguclientapp.dtos.PlatCreateDTO;
import ma.mysuguclientapp.dtos.PlatDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PlatService {
    Page<PlatDTO> getAllPlats(Long restaurantId, String categorie, String categorieProduit,
                              Boolean available, Boolean topVente, String vertical, Pageable pageable);

    PlatDTO getPlatById(Long id);

    List<PlatDTO> getPlatsByRestaurant(Long restaurantId);

    List<PlatDTO> searchPlats(String keyword, String vertical);

    PlatDTO createPlat(PlatCreateDTO platDTO, MultipartFile image);

    PlatDTO updatePlat(Long id, PlatCreateDTO platDTO, MultipartFile image);

    /** Remplace intégralement la liste des ingrédients d'un plat (liste vide = aucun ingrédient). */
    PlatDTO updateIngredients(Long id, List<String> ingredients);

    /** Bascule du flag MANUEL « Top des ventes » (choix du commerçant/admin). */
    PlatDTO updateTopVente(Long id, Boolean topVente);

    void deletePlat(Long id);

    PlatDTO updateAvailability(Long id, PlatAvailabilityUpdateDTO availabilityDTO);
}
