package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.PlatAvailabilityUpdateDTO;
import ma.mysuguclientapp.dtos.PlatCreateDTO;
import ma.mysuguclientapp.dtos.PlatDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PlatService {
    Page<PlatDTO> getAllPlats(Long restaurantId, String categorie, Boolean available, Pageable pageable);

    PlatDTO getPlatById(Long id);

    List<PlatDTO> getPlatsByRestaurant(Long restaurantId);

    List<PlatDTO> searchPlats(String keyword);

    PlatDTO createPlat(PlatCreateDTO platDTO, MultipartFile image);

    PlatDTO updatePlat(Long id, PlatCreateDTO platDTO, MultipartFile image);

    void deletePlat(Long id);

    PlatDTO updateAvailability(Long id, PlatAvailabilityUpdateDTO availabilityDTO);
}
