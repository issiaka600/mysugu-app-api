package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.ServiceCategorieDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ServiceCategorieService {
    List<ServiceCategorieDTO> getActiveServices();
    List<ServiceCategorieDTO> getAllServices();
    ServiceCategorieDTO getServiceById(Long id);
    ServiceCategorieDTO createService(
            String nom,
            String tag,
            String description,
            String icon,
            String type,
            Integer ordre,
            Boolean isActive,
            MultipartFile imageTop,
            MultipartFile imageBanner);
    ServiceCategorieDTO updateService(
            Long id,
            String nom,
            String tag,
            String description,
            String icon,
            String type,
            Integer ordre,
            Boolean isActive,
            MultipartFile imageTop,
            MultipartFile imageBanner);
    void deleteService(Long id);
}
