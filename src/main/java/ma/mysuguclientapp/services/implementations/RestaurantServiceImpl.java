package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.CategorieRestaurantDTO;
import ma.mysuguclientapp.dtos.LocalisationDTO;
import ma.mysuguclientapp.dtos.PromotionDTO;
import ma.mysuguclientapp.dtos.RestaurantCreateDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import ma.mysuguclientapp.entities.CategorieRestaurant;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.CategoriesRestaurantRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RestaurantServiceImpl implements RestaurantService {
    private final RestaurantRepository restaurantRepository;
    private final CategoriesRestaurantRepository categorieRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;

    @Override
    @Transactional(readOnly = true)
    public Page<RestaurantDTO> getAllRestaurants(Long categorieId, Double latitude,
                                                 Double longitude, Double maxDistance,
                                                 Pageable pageable) {
        Page<Restaurant> restaurants = categorieId != null
                ? restaurantRepository.findByCategorieIdAndIsActive(categorieId, true, pageable)
                : restaurantRepository.findByIsActive(true, pageable);

        List<RestaurantDTO> restaurantDTOs = restaurants.getContent().stream()
                .map(restaurant -> convertToDTO(restaurant, latitude, longitude))
                .filter(dto -> maxDistance == null || dto.getDistance() == null || dto.getDistance() <= maxDistance)
                .collect(Collectors.toList());

        return new PageImpl<>(restaurantDTOs, pageable, restaurants.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantDTO getRestaurantById(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé avec l'ID: " + id));
        return convertToDTO(restaurant, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RestaurantDTO> searchRestaurants(String keyword) {
        return restaurantRepository.searchByKeyword(keyword).stream()
                .map(restaurant -> convertToDTO(restaurant, null, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RestaurantDTO> getTopRatedRestaurants(int limit) {
        return restaurantRepository.findByIsActiveOrderByAppreciationDesc(true).stream()
                .limit(limit)
                .map(restaurant -> convertToDTO(restaurant, null, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RestaurantDTO> getNearbyRestaurants(Double latitude, Double longitude, Double radiusKm) {
        return restaurantRepository.findByIsActive(true).stream()
                .map(restaurant -> convertToDTO(restaurant, latitude, longitude))
                .filter(dto -> dto.getDistance() != null && dto.getDistance() <= radiusKm)
                .sorted(Comparator.comparing(RestaurantDTO::getDistance))
                .collect(Collectors.toList());
    }

    @Override
    public RestaurantDTO createRestaurant(RestaurantCreateDTO restaurantDTO, MultipartFile logo) {
        CategorieRestaurant categorie = null;
        if (restaurantDTO.getCategorieId() != null) {
            categorie = categorieRepository.findById(restaurantDTO.getCategorieId())
                    .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée"));
        }

        User owner = null;
        if (restaurantDTO.getOwnerId() != null) {
            owner = userRepository.findById(restaurantDTO.getOwnerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Propriétaire non trouvé"));
            if (owner.getRole() != UserRole.RESTAURANT_OWNER && owner.getRole() != UserRole.ADMIN) {
                throw new BadRequestException("L'utilisateur doit avoir le rôle RESTAURANT_OWNER");
            }
        }

        Restaurant restaurant = new Restaurant();
        restaurant.setNom(restaurantDTO.getNom());
        restaurant.setDescription(restaurantDTO.getDescription());
        restaurant.setCategorie(categorie);
        restaurant.setOwner(owner);
        restaurant.setTempsLivraisonMoyen(restaurantDTO.getTempsLivraisonMoyen());
        restaurant.setHorairesOuverture(restaurantDTO.getHorairesOuverture());
        restaurant.setIsActive(true);

        applyAutoCloseSettings(restaurant, restaurantDTO);
        applyLocalisation(restaurant, restaurantDTO.getLocalisation());

        if (Boolean.TRUE.equals(restaurantDTO.getRemoveLogo())) {
            restaurant.setLogoUrl(null);
        }

        if (logo != null && !logo.isEmpty()) {
            try {
                restaurant.setLogoUrl(minioService.uploadFile(logo, "restaurants/logos"));
            } catch (Exception e) {
                log.error("Erreur lors de l'upload du logo", e);
                throw new BadRequestException("Erreur lors de l'upload du logo");
            }
        }

        Restaurant savedRestaurant = restaurantRepository.save(restaurant);
        log.info("Restaurant créé: {}", savedRestaurant.getNom());
        return convertToDTO(savedRestaurant, null, null);
    }

    @Override
    public RestaurantDTO updateRestaurant(Long id, RestaurantCreateDTO restaurantDTO, MultipartFile logo) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));

        restaurant.setNom(restaurantDTO.getNom());
        restaurant.setDescription(restaurantDTO.getDescription());
        restaurant.setTempsLivraisonMoyen(restaurantDTO.getTempsLivraisonMoyen());
        restaurant.setHorairesOuverture(restaurantDTO.getHorairesOuverture());
        applyAutoCloseSettings(restaurant, restaurantDTO);

        if (restaurantDTO.getCategorieId() != null) {
            CategorieRestaurant categorie = categorieRepository.findById(restaurantDTO.getCategorieId())
                    .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée"));
            restaurant.setCategorie(categorie);
        }

        applyLocalisation(restaurant, restaurantDTO.getLocalisation());

        if (Boolean.TRUE.equals(restaurantDTO.getRemoveLogo()) && restaurant.getLogoUrl() != null) {
            try {
                minioService.deleteFile(restaurant.getLogoUrl());
                restaurant.setLogoUrl(null);
            } catch (Exception e) {
                log.error("Erreur lors de la suppression du logo", e);
                throw new BadRequestException("Erreur lors de la suppression du logo");
            }
        }

        if (logo != null && !logo.isEmpty()) {
            try {
                if (restaurant.getLogoUrl() != null) {
                    minioService.deleteFile(restaurant.getLogoUrl());
                }
                restaurant.setLogoUrl(minioService.uploadFile(logo, "restaurants/logos"));
            } catch (Exception e) {
                log.error("Erreur lors de l'upload du logo", e);
                throw new BadRequestException("Erreur lors de l'upload du logo");
            }
        }

        Restaurant updatedRestaurant = restaurantRepository.save(restaurant);
        log.info("Restaurant mis à jour: {}", updatedRestaurant.getNom());
        return convertToDTO(updatedRestaurant, null, null);
    }

    @Override
    public void deleteRestaurant(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));

        if (restaurant.getLogoUrl() != null) {
            try {
                minioService.deleteFile(restaurant.getLogoUrl());
            } catch (Exception e) {
                log.warn("Erreur lors de la suppression du logo", e);
            }
        }

        restaurantRepository.delete(restaurant);
        log.info("Restaurant supprimé: {}", restaurant.getNom());
    }

    @Override
    public RestaurantDTO toggleRestaurantStatus(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));

        restaurant.setIsActive(!restaurant.getIsActive());
        Restaurant updatedRestaurant = restaurantRepository.save(restaurant);
        log.info("Statut du restaurant {} changé à: {}", restaurant.getNom(), restaurant.getIsActive());

        return convertToDTO(updatedRestaurant, null, null);
    }

    private void applyAutoCloseSettings(Restaurant restaurant, RestaurantCreateDTO restaurantDTO) {
        boolean autoCloseEnabled = Boolean.TRUE.equals(restaurantDTO.getAutoCloseEnabled());
        restaurant.setAutoCloseEnabled(autoCloseEnabled);
        restaurant.setHeureOuverture(restaurantDTO.getHeureOuverture());
        restaurant.setHeureFermeture(restaurantDTO.getHeureFermeture());

        if (autoCloseEnabled && (restaurantDTO.getHeureOuverture() == null || restaurantDTO.getHeureFermeture() == null)) {
            throw new BadRequestException("Les heures d'ouverture et de fermeture sont requises quand la fermeture automatique est activée");
        }
    }

    private void applyLocalisation(Restaurant restaurant, LocalisationDTO localisationDTO) {
        if (localisationDTO == null) {
            return;
        }

        Localisation localisation = restaurant.getLocalisation();
        if (localisation == null) {
            localisation = new Localisation();
        }

        localisation.setLatitude(localisationDTO.getLatitude());
        localisation.setLongitude(localisationDTO.getLongitude());
        localisation.setAdresse(localisationDTO.getAdresse());
        localisation.setVille(localisationDTO.getVille());
        localisation.setCodePostal(localisationDTO.getCodePostal());
        localisation.setPays(localisationDTO.getPays());
        restaurant.setLocalisation(localisation);
    }

    private RestaurantDTO convertToDTO(Restaurant restaurant, Double userLat, Double userLon) {
        RestaurantDTO dto = new RestaurantDTO();
        dto.setId(restaurant.getId());
        dto.setNom(restaurant.getNom());
        dto.setDescription(restaurant.getDescription());
        dto.setLogoObjectName(restaurant.getLogoUrl());
        dto.setLogoUrl(minioService.buildPublicFileUrl(restaurant.getLogoUrl()));
        dto.setAppreciation(restaurant.getAppreciation());
        dto.setNombreAvis(restaurant.getNombreAvis());
        dto.setTempsLivraisonMoyen(restaurant.getTempsLivraisonMoyen());
        dto.setAutoCloseEnabled(restaurant.getAutoCloseEnabled());
        dto.setHeureOuverture(restaurant.getHeureOuverture());
        dto.setHeureFermeture(restaurant.getHeureFermeture());

        boolean openNow = isRestaurantOpenNow(restaurant);
        dto.setOpenNow(openNow);
        dto.setIsActive(openNow);
        dto.setCreatedAt(restaurant.getCreatedAt());

        if (restaurant.getLocalisation() != null) {
            LocalisationDTO locDTO = new LocalisationDTO();
            locDTO.setLatitude(restaurant.getLocalisation().getLatitude());
            locDTO.setLongitude(restaurant.getLocalisation().getLongitude());
            locDTO.setAdresse(restaurant.getLocalisation().getAdresse());
            locDTO.setVille(restaurant.getLocalisation().getVille());
            locDTO.setCodePostal(restaurant.getLocalisation().getCodePostal());
            locDTO.setPays(restaurant.getLocalisation().getPays());
            dto.setLocalisation(locDTO);

            if (userLat != null && userLon != null) {
                dto.setDistance(calculateDistance(
                        userLat, userLon,
                        restaurant.getLocalisation().getLatitude(),
                        restaurant.getLocalisation().getLongitude()
                ));
            }
        }

        if (restaurant.getCategorie() != null) {
            CategorieRestaurantDTO catDTO = new CategorieRestaurantDTO();
            catDTO.setId(restaurant.getCategorie().getId());
            catDTO.setNom(restaurant.getCategorie().getNom());
            catDTO.setDescription(restaurant.getCategorie().getDescription());
            catDTO.setImageUrl(restaurant.getCategorie().getImageUrl());
            dto.setCategorie(catDTO);
        }

        if (restaurant.getPromotion() != null && restaurant.getPromotion().getIsActive()) {
            PromotionDTO promoDTO = new PromotionDTO();
            promoDTO.setId(restaurant.getPromotion().getId());
            promoDTO.setPourcentage(restaurant.getPromotion().getPourcentage());
            promoDTO.setDateDebut(restaurant.getPromotion().getDateDebut());
            promoDTO.setDateFin(restaurant.getPromotion().getDateFin());
            promoDTO.setDescription(restaurant.getPromotion().getDescription());
            promoDTO.setIsActive(restaurant.getPromotion().getIsActive());
            dto.setPromotion(promoDTO);
        }

        return dto;
    }

    private boolean isRestaurantOpenNow(Restaurant restaurant) {
        if (!Boolean.TRUE.equals(restaurant.getIsActive())) {
            return false;
        }

        if (!Boolean.TRUE.equals(restaurant.getAutoCloseEnabled())) {
            return true;
        }

        LocalTime opening = restaurant.getHeureOuverture();
        LocalTime closing = restaurant.getHeureFermeture();
        if (opening == null || closing == null) {
            return true;
        }

        LocalTime now = LocalTime.now();
        if (opening.equals(closing)) {
            return true;
        }
        if (opening.isBefore(closing)) {
            return !now.isBefore(opening) && now.isBefore(closing);
        }

        return !now.isBefore(opening) || now.isBefore(closing);
    }

    private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return Double.MAX_VALUE;
        }

        final int r = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}
