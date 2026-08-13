package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.CategorieRestaurantDTO;
import ma.mysuguclientapp.dtos.LocalisationDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import ma.mysuguclientapp.entities.CategorieRestaurant;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.CategoriesRestaurantRepository;
import ma.mysuguclientapp.services.interfaces.CategorieRestaurantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategorieRestaurantServiceImpl implements CategorieRestaurantService {
    private final CategoriesRestaurantRepository categorieRepository;
    private final MinioService minioService;

    @Override
    @Transactional(readOnly = true)
    public List<CategorieRestaurantDTO> getAllCategories(String vertical) {
        List<CategorieRestaurant> categories;
        if ("ALL".equalsIgnoreCase(vertical)) {
            categories = categorieRepository.findAll();
        } else {
            Vertical v = RestaurantServiceImpl.parseVertical(vertical);
            categories = categorieRepository.findByVerticalEffectif(v);
        }
        return categories.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CategorieRestaurantDTO getCategorieById(Long id) {
        CategorieRestaurant categorie = categorieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée avec l'ID: " + id));
        return convertToDTO(categorie);
    }

    @Override
    public CategorieRestaurantDTO createCategorie(
            String nom,
            String description,
            MultipartFile image,
            MultipartFile imageTop,
            MultipartFile imageBanner) {
        // Vérifier si le nom existe déjà
        if (categorieRepository.findByNom(nom).isPresent()) {
            throw new BadRequestException("Une catégorie avec ce nom existe déjà");
        }

        CategorieRestaurant categorie = new CategorieRestaurant();
        categorie.setNom(nom);
        categorie.setDescription(description);

        categorie.setImageUrl(uploadCategorieImage(image));
        categorie.setImageTopUrl(uploadCategorieImage(imageTop));
        categorie.setImageBannerUrl(uploadCategorieImage(imageBanner));

        CategorieRestaurant savedCategorie = categorieRepository.save(categorie);
        log.info("Catégorie créée: {}", savedCategorie.getNom());

        return convertToDTO(savedCategorie);
    }

    @Override
    public CategorieRestaurantDTO updateCategorie(
            Long id,
            String nom,
            String description,
            MultipartFile image,
            MultipartFile imageTop,
            MultipartFile imageBanner) {
        CategorieRestaurant categorie = categorieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée"));

        // Vérifier si le nouveau nom existe déjà (sauf pour cette catégorie)
        categorieRepository.findByNom(nom).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BadRequestException("Une catégorie avec ce nom existe déjà");
            }
        });

        categorie.setNom(nom);
        categorie.setDescription(description);

        categorie.setImageUrl(replaceCategorieImage(categorie.getImageUrl(), image));
        categorie.setImageTopUrl(replaceCategorieImage(categorie.getImageTopUrl(), imageTop));
        categorie.setImageBannerUrl(replaceCategorieImage(categorie.getImageBannerUrl(), imageBanner));

        CategorieRestaurant updatedCategorie = categorieRepository.save(categorie);
        log.info("Catégorie mise à jour: {}", updatedCategorie.getNom());

        return convertToDTO(updatedCategorie);
    }

    @Override
    public void deleteCategorie(Long id) {
        CategorieRestaurant categorie = categorieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée"));

        // Vérifier s'il y a des restaurants dans cette catégorie
        if (!categorie.getRestaurants().isEmpty()) {
            throw new BadRequestException("Impossible de supprimer une catégorie contenant des restaurants");
        }

        // Supprimer l'image de MinIO
        if (categorie.getImageUrl() != null) {
            try {
                minioService.deleteFile(categorie.getImageUrl());
            } catch (Exception e) {
                log.warn("Erreur lors de la suppression de l'image", e);
            }
        }
        if (categorie.getImageTopUrl() != null) {
            try {
                minioService.deleteFile(categorie.getImageTopUrl());
            } catch (Exception e) {
                log.warn("Erreur lors de la suppression de l'image top", e);
            }
        }
        if (categorie.getImageBannerUrl() != null) {
            try {
                minioService.deleteFile(categorie.getImageBannerUrl());
            } catch (Exception e) {
                log.warn("Erreur lors de la suppression de l'image bannière", e);
            }
        }

        categorieRepository.delete(categorie);
        log.info("Catégorie supprimée: {}", categorie.getNom());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RestaurantDTO> getRestaurantsByCategorie(Long categorieId) {
        CategorieRestaurant categorie = categorieRepository.findById(categorieId)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée"));

        return categorie.getRestaurants().stream()
                .filter(Restaurant::getIsActive)
                .map(this::convertRestaurantToDTO)
                .collect(Collectors.toList());
    }

    // ========== MÉTHODES UTILITAIRES ==========

    private CategorieRestaurantDTO convertToDTO(CategorieRestaurant categorie) {
        CategorieRestaurantDTO dto = new CategorieRestaurantDTO();
        dto.setId(categorie.getId());
        dto.setNom(categorie.getNom());
        dto.setDescription(categorie.getDescription());
        dto.setImageUrl(minioService.buildPublicFileUrl(categorie.getImageUrl()));
        dto.setImageTopUrl(minioService.buildPublicFileUrl(categorie.getImageTopUrl()));
        dto.setImageBannerUrl(minioService.buildPublicFileUrl(categorie.getImageBannerUrl()));
        dto.setVertical(categorie.getVertical() != null ? categorie.getVertical().name() : "RESTAURANT");

        if (categorie.getRestaurants() != null) {
            dto.setNombreRestaurants(categorie.getRestaurants().size());
        }

        return dto;
    }

    private String uploadCategorieImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return null;
        }
        try {
            return minioService.uploadFile(image, "categories");
        } catch (Exception e) {
            log.error("Erreur lors de l'upload de l'image catégorie", e);
            throw new BadRequestException("Erreur lors de l'upload de l'image");
        }
    }

    private String replaceCategorieImage(String currentImageUrl, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return currentImageUrl;
        }
        try {
            if (currentImageUrl != null) {
                minioService.deleteFile(currentImageUrl);
            }
            return minioService.uploadFile(image, "categories");
        } catch (Exception e) {
            log.error("Erreur lors du remplacement de l'image catégorie", e);
            throw new BadRequestException("Erreur lors de l'upload de l'image");
        }
    }

    private RestaurantDTO convertRestaurantToDTO(Restaurant restaurant) {
        RestaurantDTO dto = new RestaurantDTO();
        dto.setId(restaurant.getId());
        dto.setNom(restaurant.getNom());
        dto.setDescription(restaurant.getDescription());
        dto.setLogoUrl(restaurant.getLogoUrl());
        dto.setAppreciation(restaurant.getAppreciation());
        dto.setNombreAvis(restaurant.getNombreAvis());
        dto.setTempsLivraisonMoyen(restaurant.getTempsLivraisonMoyen());
        dto.setIsActive(restaurant.getIsActive());

        if (restaurant.getLocalisation() != null) {
            LocalisationDTO locDTO = new LocalisationDTO();
            locDTO.setLatitude(restaurant.getLocalisation().getLatitude());
            locDTO.setLongitude(restaurant.getLocalisation().getLongitude());
            locDTO.setAdresse(restaurant.getLocalisation().getAdresse());
            dto.setLocalisation(locDTO);
        }

        return dto;
    }
}
