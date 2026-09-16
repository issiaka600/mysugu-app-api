package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.ServiceCategorieDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ServiceCategorieService {
    List<ServiceCategorieDTO> getActiveServices();
    List<ServiceCategorieDTO> getAllServices();
    ServiceCategorieDTO getServiceById(Long id);
    /** @param vertical verticale ouverte par la tuile, optionnel : absent/vide ⇒ aucune (null), inconnu ⇒ 400. */
    ServiceCategorieDTO createService(
            String nom,
            String tag,
            String description,
            String icon,
            String type,
            String vertical,
            Integer ordre,
            Boolean isActive,
            MultipartFile imageTop,
            MultipartFile imageBanner);
    /** @param vertical verticale ouverte par la tuile, optionnel : absent ⇒ inchangée, vide ⇒ inchangée, inconnu ⇒ 400. */
    ServiceCategorieDTO updateService(
            Long id,
            String nom,
            String tag,
            String description,
            String icon,
            String type,
            String vertical,
            Integer ordre,
            Boolean isActive,
            MultipartFile imageTop,
            MultipartFile imageBanner,
            Boolean removeImageTop,
            Boolean removeImageBanner);
    void deleteService(Long id);
}
