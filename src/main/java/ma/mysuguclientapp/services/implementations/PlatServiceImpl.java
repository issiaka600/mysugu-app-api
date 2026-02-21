package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.PlatCreateDTO;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.CategoriePlat;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.interfaces.PlatService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PlatServiceImpl implements PlatService {
    private final PlatRepository platRepository;
    private final RestaurantRepository restaurantRepository;
    private final MinioService minioService;

    @Override
    @Transactional(readOnly = true)
    public Page<PlatDTO> getAllPlats(Long restaurantId, String categorie, Boolean available, Pageable pageable) {
        Page<Plat> plats;

        if (restaurantId != null && categorie != null && available != null) {
            CategoriePlat cat = CategoriePlat.valueOf(categorie);
            plats = platRepository.findByRestaurantIdAndCategoriePlatAndIsAvailable(
                    restaurantId, cat, available, pageable);
        } else if (restaurantId != null) {
            plats = platRepository.findByRestaurantId(restaurantId, pageable);
        } else if (available != null) {
            plats = platRepository.findByIsAvailable(available, pageable);
        } else {
            plats = platRepository.findAll(pageable);
        }

        return plats.map(this::convertToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public PlatDTO getPlatById(Long id) {
        Plat plat = platRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé avec l'ID: " + id));
        return convertToDTO(plat);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatDTO> getPlatsByRestaurant(Long restaurantId) {
        List<Plat> plats = platRepository.findByRestaurantIdAndIsAvailable(restaurantId, true);
        return plats.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatDTO> searchPlats(String keyword) {
        List<Plat> plats = platRepository.searchByKeyword(keyword);
        return plats.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public PlatDTO createPlat(PlatCreateDTO platDTO, MultipartFile image) {
        // Vérifier le restaurant
        Restaurant restaurant = restaurantRepository.findById(platDTO.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));

        // Créer le plat
        Plat plat = new Plat();
        plat.setNom(platDTO.getNom());
        plat.setDescription(platDTO.getDescription());
        plat.setPrix(platDTO.getPrix());
        plat.setRestaurant(restaurant);
        plat.setTempsPreparation(platDTO.getTempsPreparation());
        plat.setIsAvailable(true);

        // Catégorie
        if (platDTO.getCategoriePlat() != null) {
            try {
                plat.setCategoriePlat(CategoriePlat.valueOf(platDTO.getCategoriePlat()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Catégorie de plat invalide: " + platDTO.getCategoriePlat());
            }
        }

        // Ingrédients
        if (platDTO.getIngredients() != null) {
            plat.setIngredients(platDTO.getIngredients());
        }

        // Upload image
        if (image != null && !image.isEmpty()) {
            try {
                String imageUrl = minioService.uploadFile(image, "plats");
                plat.setImageUrl(imageUrl);
            } catch (Exception e) {
                log.error("Erreur lors de l'upload de l'image", e);
                throw new BadRequestException("Erreur lors de l'upload de l'image");
            }
        }

        Plat savedPlat = platRepository.save(plat);
        log.info("Plat créé: {}", savedPlat.getNom());

        return convertToDTO(savedPlat);
    }

    @Override
    public PlatDTO updatePlat(Long id, PlatCreateDTO platDTO, MultipartFile image) {
        Plat plat = platRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé"));

        // Mettre à jour les champs
        if (platDTO != null){
            plat.setNom(platDTO.getNom());
            plat.setDescription(platDTO.getDescription());
            plat.setPrix(platDTO.getPrix());
            plat.setTempsPreparation(platDTO.getTempsPreparation());

            // Catégorie
            if (platDTO.getCategoriePlat() != null) {
                try {
                    plat.setCategoriePlat(CategoriePlat.valueOf(platDTO.getCategoriePlat()));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Catégorie de plat invalide");
                }
            }

            // Ingrédients
            if (platDTO.getIngredients() != null) {
                plat.setIngredients(platDTO.getIngredients());
            }

        }


        // Upload nouvelle image si fournie
        if (image != null && !image.isEmpty()) {
            try {
                // Supprimer l'ancienne image
                if (plat.getImageUrl() != null) {
                    minioService.deleteFile(plat.getImageUrl());
                }

                String imageUrl = minioService.uploadFile(image, "plats");
                plat.setImageUrl(imageUrl);
            } catch (Exception e) {
                log.error("Erreur lors de l'upload de l'image", e);
                throw new BadRequestException("Erreur lors de l'upload de l'image");
            }
        }

        Plat updatedPlat = platRepository.save(plat);
        log.info("Plat mis à jour: {}", updatedPlat.getNom());

        return convertToDTO(updatedPlat);
    }

    @Override
    public void deletePlat(Long id) {
        Plat plat = platRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé"));

        // Supprimer l'image de MinIO
        if (plat.getImageUrl() != null) {
            try {
                minioService.deleteFile(plat.getImageUrl());
            } catch (Exception e) {
                log.warn("Erreur lors de la suppression de l'image", e);
            }
        }

        platRepository.delete(plat);
        log.info("Plat supprimé: {}", plat.getNom());
    }

    @Override
    public PlatDTO toggleAvailability(Long id) {
        Plat plat = platRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé"));

        plat.setIsAvailable(!plat.getIsAvailable());
        Plat updatedPlat = platRepository.save(plat);

        log.info("Disponibilité du plat {} changée à: {}", plat.getNom(), plat.getIsAvailable());

        return convertToDTO(updatedPlat);
    }

    // ========== MÉTHODES UTILITAIRES ==========

    private PlatDTO convertToDTO(Plat plat) {
        PlatDTO dto = new PlatDTO();
        dto.setId(plat.getId());
        dto.setNom(plat.getNom());
        dto.setDescription(plat.getDescription());
        dto.setPrix(plat.getPrix());
        dto.setImageUrl(plat.getImageUrl());
        dto.setIngredients(plat.getIngredients());
        dto.setIsAvailable(plat.getIsAvailable());
        dto.setTempsPreparation(plat.getTempsPreparation());

        if (plat.getCategoriePlat() != null) {
            dto.setCategoriePlat(plat.getCategoriePlat().name());
        }

        if (plat.getRestaurant() != null) {
            dto.setRestaurantId(plat.getRestaurant().getId());
            dto.setRestaurantNom(plat.getRestaurant().getNom());
        }

        return dto;
    }
}
