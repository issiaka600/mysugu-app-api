package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.ServiceCategorieDTO;
import ma.mysuguclientapp.entities.ServiceCategorie;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.ServiceCategorieRepository;
import ma.mysuguclientapp.services.interfaces.ServiceCategorieService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ServiceCategorieServiceImpl implements ServiceCategorieService {

    private final ServiceCategorieRepository repository;
    private final MinioService minioService;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceCategorieDTO> getActiveServices() {
        return repository.findByIsActiveTrueOrderByOrdreAscNomAsc().stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceCategorieDTO> getAllServices() {
        return repository.findAllByOrderByOrdreAscNomAsc().stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceCategorieDTO getServiceById(Long id) {
        return toDTO(findService(id));
    }

    @Override
    public ServiceCategorieDTO createService(
            String nom,
            String tag,
            String description,
            String icon,
            String type,
            Integer ordre,
            Boolean isActive,
            MultipartFile imageTop,
            MultipartFile imageBanner) {
        repository.findByNom(nom).ifPresent(existing -> {
            throw new BadRequestException("Un service avec ce nom existe déjà");
        });

        ServiceCategorie service = new ServiceCategorie();
        applyFields(service, nom, tag, description, icon, type, ordre, isActive);
        service.setImageTopUrl(uploadImage(imageTop));
        service.setImageBannerUrl(uploadImage(imageBanner));
        return toDTO(repository.save(service));
    }

    @Override
    public ServiceCategorieDTO updateService(
            Long id,
            String nom,
            String tag,
            String description,
            String icon,
            String type,
            Integer ordre,
            Boolean isActive,
            MultipartFile imageTop,
            MultipartFile imageBanner) {
        ServiceCategorie service = findService(id);
        repository.findByNom(nom).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BadRequestException("Un service avec ce nom existe déjà");
            }
        });

        applyFields(service, nom, tag, description, icon, type, ordre, isActive);
        service.setImageTopUrl(replaceImage(service.getImageTopUrl(), imageTop));
        service.setImageBannerUrl(replaceImage(service.getImageBannerUrl(), imageBanner));
        return toDTO(repository.save(service));
    }

    @Override
    public void deleteService(Long id) {
        ServiceCategorie service = findService(id);
        deleteImage(service.getImageTopUrl());
        deleteImage(service.getImageBannerUrl());
        repository.delete(service);
    }

    private ServiceCategorie findService(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service non trouvé avec l'ID: " + id));
    }

    private void applyFields(
            ServiceCategorie service,
            String nom,
            String tag,
            String description,
            String icon,
            String type,
            Integer ordre,
            Boolean isActive) {
        service.setNom(nom);
        service.setTag(tag);
        service.setDescription(description);
        service.setIcon(icon);
        service.setType(type);
        service.setOrdre(ordre != null ? ordre : 0);
        service.setIsActive(isActive == null || isActive);
    }

    private String uploadImage(MultipartFile image) {
        if (image == null || image.isEmpty()) return null;
        try {
            return minioService.uploadFile(image, "services");
        } catch (Exception e) {
            log.error("Erreur lors de l'upload de l'image service", e);
            throw new BadRequestException("Erreur lors de l'upload de l'image");
        }
    }

    private String replaceImage(String currentImageUrl, MultipartFile image) {
        if (image == null || image.isEmpty()) return currentImageUrl;
        deleteImage(currentImageUrl);
        return uploadImage(image);
    }

    private void deleteImage(String imageUrl) {
        if (imageUrl == null) return;
        try {
            minioService.deleteFile(imageUrl);
        } catch (Exception e) {
            log.warn("Erreur lors de la suppression de l'image service", e);
        }
    }

    private ServiceCategorieDTO toDTO(ServiceCategorie service) {
        ServiceCategorieDTO dto = new ServiceCategorieDTO();
        dto.setId(service.getId());
        dto.setNom(service.getNom());
        dto.setTag(service.getTag());
        dto.setDescription(service.getDescription());
        dto.setImageTopUrl(minioService.buildPublicFileUrl(service.getImageTopUrl()));
        dto.setImageBannerUrl(minioService.buildPublicFileUrl(service.getImageBannerUrl()));
        dto.setIcon(service.getIcon());
        dto.setType(service.getType());
        dto.setVertical(service.getVertical() != null ? service.getVertical().name() : null);
        dto.setOrdre(service.getOrdre());
        dto.setIsActive(service.getIsActive());
        return dto;
    }
}
