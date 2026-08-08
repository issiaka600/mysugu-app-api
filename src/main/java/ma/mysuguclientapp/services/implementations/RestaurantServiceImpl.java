package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.CategorieRestaurantDTO;
import ma.mysuguclientapp.dtos.LocalisationDTO;
import ma.mysuguclientapp.dtos.PromotionDTO;
import ma.mysuguclientapp.dtos.RestaurantCreateDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import ma.mysuguclientapp.dtos.ZoneDeploiementDTO;
import ma.mysuguclientapp.entities.CategorieRestaurant;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.entities.ZoneDeploiement;
import ma.mysuguclientapp.enumerations.StatutRestaurant;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.CategoriesRestaurantRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.repositories.ZoneDeploiementRepository;
import ma.mysuguclientapp.services.interfaces.OwnerProvisioningService;
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
    private final ZoneDeploiementRepository zoneDeploiementRepository;
    private final OwnerProvisioningService ownerProvisioningService;
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public Page<RestaurantDTO> getAllRestaurants(Long categorieId, Double latitude,
                                                 Double longitude, Double maxDistance,
                                                 String vertical, Pageable pageable) {
        Page<Restaurant> restaurants;
        if (categorieId != null) {
            restaurants = restaurantRepository.findByCategorieIdAndIsActive(categorieId, true, pageable);
        } else if ("ALL".equalsIgnoreCase(vertical)) {
            restaurants = restaurantRepository.findByIsActive(true, pageable);
        } else {
            Vertical v = parseVertical(vertical);
            restaurants = restaurantRepository.findByVerticalAndIsActive(v, true, pageable);
        }

        List<RestaurantDTO> restaurantDTOs = restaurants.getContent().stream()
                .map(restaurant -> convertToDTO(restaurant, latitude, longitude))
                .filter(dto -> maxDistance == null || dto.getDistance() == null || dto.getDistance() <= maxDistance)
                .collect(Collectors.toList());

        return new PageImpl<>(restaurantDTOs, pageable, restaurants.getTotalElements());
    }

    private Vertical parseVertical(String value) {
        if (value == null || value.isBlank()) {
            return Vertical.RESTAURANT;
        }
        try {
            return Vertical.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Vertical invalide: " + value);
        }
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

        User owner = ownerProvisioningService.resolveOrCreateOwner(restaurantDTO);

        Restaurant restaurant = new Restaurant();
        restaurant.setNom(restaurantDTO.getNom());
        restaurant.setDescription(restaurantDTO.getDescription());
        restaurant.setCategorie(categorie);
        restaurant.setOwner(owner);
        restaurant.setVertical(parseVertical(restaurantDTO.getVertical()));
        restaurant.setTempsLivraisonMoyen(restaurantDTO.getTempsLivraisonMoyen());
        restaurant.setHorairesOuverture(restaurantDTO.getHorairesOuverture());
        restaurant.setIsActive(true);

        applyAutoCloseSettings(restaurant, restaurantDTO);
        applyLocalisation(restaurant, restaurantDTO.getLocalisation());
        applyZoneDeploiement(restaurant, restaurantDTO.getZoneDeploiementId());

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

        if (restaurantDTO.getVertical() != null && !restaurantDTO.getVertical().isBlank()) {
            restaurant.setVertical(parseVertical(restaurantDTO.getVertical()));
        }

        if (restaurantDTO.getOwnerId() != null) {
            User owner = userRepository.findById(restaurantDTO.getOwnerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Propriétaire non trouvé"));
            if (owner.getRole() != UserRole.RESTAURANT_OWNER && owner.getRole() != UserRole.ADMIN) {
                throw new BadRequestException("L'utilisateur doit avoir le rôle RESTAURANT_OWNER");
            }
            restaurant.setOwner(owner);
        }

        applyLocalisation(restaurant, restaurantDTO.getLocalisation());
        applyZoneDeploiement(restaurant, restaurantDTO.getZoneDeploiementId());

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
    public RestaurantDTO updateRestaurantBanner(Long id, MultipartFile banner) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));
        if (banner == null || banner.isEmpty()) {
            return convertToDTO(restaurant, null, null);
        }

        final String previousBanner = restaurant.getBannerUrl();
        final String uploadedBanner;
        try {
            uploadedBanner = minioService.uploadFile(banner, "restaurants/banners");
        } catch (Exception e) {
            log.error("Erreur lors de l'upload de la bannière", e);
            throw new BadRequestException("Erreur lors de l'upload de la bannière");
        }

        restaurant.setBannerUrl(uploadedBanner);
        Restaurant updatedRestaurant = restaurantRepository.save(restaurant);
        if (previousBanner != null && !previousBanner.equals(uploadedBanner)) {
            try {
                minioService.deleteFile(previousBanner);
            } catch (Exception e) {
                log.warn("Erreur lors de la suppression de l'ancienne bannière", e);
            }
        }
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
        if (restaurant.getBannerUrl() != null) {
            try {
                minioService.deleteFile(restaurant.getBannerUrl());
            } catch (Exception e) {
                log.warn("Erreur lors de la suppression de la bannière", e);
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

    @Override
    public RestaurantDTO setCommissionPourcentage(Long id, java.math.BigDecimal pourcentage) {
        if (pourcentage == null || pourcentage.compareTo(java.math.BigDecimal.ZERO) < 0
                || pourcentage.compareTo(new java.math.BigDecimal("100")) > 0) {
            throw new BadRequestException("Le pourcentage de commission doit être compris entre 0 et 100");
        }
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));
        restaurant.setCommissionPourcentage(pourcentage);
        Restaurant updated = restaurantRepository.save(restaurant);
        log.info("Commission du restaurant {} définie à {}%", restaurant.getNom(), pourcentage);
        return convertToDTO(updated, null, null);
    }

    // ===================== Onboarding restaurateur =====================

    @Override
    public RestaurantDTO soumettreOnboarding(String ownerEmail, RestaurantCreateDTO dto,
                                             MultipartFile logo, List<MultipartFile> justificatifs) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurateur non trouvé"));
        if (owner.getRole() != UserRole.RESTAURANT_OWNER && owner.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("Seul un compte restaurateur peut soumettre un restaurant");
        }
        if (restaurantRepository.findByOwnerId(owner.getId()).isPresent()) {
            throw new BadRequestException("Un restaurant est déjà rattaché à ce compte");
        }
        if (dto.getNom() == null || dto.getNom().isBlank()) {
            throw new BadRequestException("Le nom du restaurant est obligatoire");
        }

        CategorieRestaurant categorie = null;
        if (dto.getCategorieId() != null) {
            categorie = categorieRepository.findById(dto.getCategorieId())
                    .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée"));
        }

        Restaurant restaurant = new Restaurant();
        restaurant.setNom(dto.getNom());
        restaurant.setDescription(dto.getDescription());
        restaurant.setCategorie(categorie);
        restaurant.setOwner(owner);
        restaurant.setVertical(parseVertical(dto.getVertical()));
        restaurant.setTempsLivraisonMoyen(dto.getTempsLivraisonMoyen());
        restaurant.setHorairesOuverture(dto.getHorairesOuverture());
        // En attente de validation admin : non actif / non visible tant que non approuvé.
        restaurant.setIsActive(false);
        restaurant.setStatutApprobation(StatutRestaurant.EN_ATTENTE);

        applyAutoCloseSettings(restaurant, dto);
        applyLocalisation(restaurant, dto.getLocalisation());
        applyZoneDeploiement(restaurant, dto.getZoneDeploiementId());

        if (logo != null && !logo.isEmpty()) {
            restaurant.setLogoUrl(uploadOrThrow(logo, "restaurants/logos"));
        }
        restaurant.getJustificatifs().addAll(uploadJustificatifs(justificatifs));

        Restaurant saved = restaurantRepository.save(restaurant);
        log.info("Onboarding soumis: restaurant '{}' par {}", saved.getNom(), ownerEmail);
        return convertToDTO(saved, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantDTO getMonRestaurant(String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurateur non trouvé"));
        Restaurant restaurant = restaurantRepository.findByOwnerId(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Aucun restaurant rattaché à ce compte"));
        return convertToDTO(restaurant, null, null);
    }

    @Override
    public RestaurantDTO ajouterJustificatifs(String ownerEmail, List<MultipartFile> justificatifs) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurateur non trouvé"));
        Restaurant restaurant = restaurantRepository.findByOwnerId(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Aucun restaurant rattaché à ce compte"));
        if (justificatifs == null || justificatifs.isEmpty()) {
            throw new BadRequestException("Aucun justificatif fourni");
        }
        restaurant.getJustificatifs().addAll(uploadJustificatifs(justificatifs));
        // Repasse en attente après ajout de complément.
        if (restaurant.getStatutApprobation() == StatutRestaurant.COMPLEMENT_REQUIS) {
            restaurant.setStatutApprobation(StatutRestaurant.EN_ATTENTE);
        }
        Restaurant saved = restaurantRepository.save(restaurant);
        return convertToDTO(saved, null, null);
    }

    // ===================== Revue admin =====================

    @Override
    @Transactional(readOnly = true)
    public List<RestaurantDTO> getRestaurantsAReviser() {
        return restaurantRepository.findByStatutApprobationInOrderByDateRevueAscIdAsc(
                        List.of(StatutRestaurant.EN_ATTENTE, StatutRestaurant.COMPLEMENT_REQUIS))
                .stream()
                .map(r -> convertToDTO(r, null, null))
                .collect(Collectors.toList());
    }

    @Override
    public RestaurantDTO approuverRestaurant(Long id, Long adminId) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));
        restaurant.setStatutApprobation(StatutRestaurant.APPROUVE);
        restaurant.setMotifRevue(null);
        restaurant.setIsActive(true);
        restaurant.setDateRevue(java.time.LocalDateTime.now());
        restaurant.setRevuePar(adminId);
        Restaurant saved = restaurantRepository.save(restaurant);
        notifierRestaurateur(saved, "Votre restaurant a été approuvé",
                "Félicitations, votre restaurant \"" + saved.getNom()
                        + "\" a été approuvé et est désormais visible sur MySugu.");
        log.info("Restaurant {} approuvé par admin {}", id, adminId);
        return convertToDTO(saved, null, null);
    }

    @Override
    public RestaurantDTO rejeterRestaurant(Long id, Long adminId, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new BadRequestException("Un motif de rejet est obligatoire");
        }
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));
        restaurant.setStatutApprobation(StatutRestaurant.REJETE);
        restaurant.setMotifRevue(motif);
        restaurant.setIsActive(false);
        restaurant.setDateRevue(java.time.LocalDateTime.now());
        restaurant.setRevuePar(adminId);
        Restaurant saved = restaurantRepository.save(restaurant);
        notifierRestaurateur(saved, "Votre demande a été rejetée",
                "Votre restaurant \"" + saved.getNom() + "\" n'a pas pu être validé. Motif : " + motif);
        log.info("Restaurant {} rejeté par admin {}", id, adminId);
        return convertToDTO(saved, null, null);
    }

    @Override
    public RestaurantDTO demanderComplement(Long id, Long adminId, String message) {
        if (message == null || message.isBlank()) {
            throw new BadRequestException("Précisez les éléments demandés au restaurateur");
        }
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));
        restaurant.setStatutApprobation(StatutRestaurant.COMPLEMENT_REQUIS);
        restaurant.setMotifRevue(message);
        restaurant.setIsActive(false);
        restaurant.setDateRevue(java.time.LocalDateTime.now());
        restaurant.setRevuePar(adminId);
        Restaurant saved = restaurantRepository.save(restaurant);
        notifierRestaurateur(saved, "Justificatifs complémentaires requis",
                "Pour valider votre restaurant \"" + saved.getNom()
                        + "\", merci de fournir : " + message);
        log.info("Complément demandé pour restaurant {} par admin {}", id, adminId);
        return convertToDTO(saved, null, null);
    }

    private List<String> uploadJustificatifs(List<MultipartFile> justificatifs) {
        List<String> objectNames = new java.util.ArrayList<>();
        if (justificatifs == null) {
            return objectNames;
        }
        for (MultipartFile f : justificatifs) {
            if (f != null && !f.isEmpty()) {
                objectNames.add(uploadOrThrow(f, "restaurants/justificatifs"));
            }
        }
        return objectNames;
    }

    private String uploadOrThrow(MultipartFile file, String folder) {
        try {
            return minioService.uploadFile(file, folder);
        } catch (Exception e) {
            log.error("Erreur lors de l'upload du fichier ({})", folder, e);
            throw new BadRequestException("Erreur lors de l'upload du fichier");
        }
    }

    private void notifierRestaurateur(Restaurant restaurant, String sujet, String message) {
        try {
            if (restaurant.getOwner() != null && restaurant.getOwner().getEmail() != null) {
                emailService.envoyerNotificationRevueRestaurant(
                        restaurant.getOwner().getEmail(), sujet, message);
            }
        } catch (Exception e) {
            log.warn("Notification restaurateur non envoyée: {}", e.getMessage());
        }
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

    private void applyZoneDeploiement(Restaurant restaurant, Long zoneDeploiementId) {
        if (zoneDeploiementId == null) {
            restaurant.setZoneDeploiement(null);
            return;
        }
        ZoneDeploiement zone = zoneDeploiementRepository.findById(zoneDeploiementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Zone de déploiement introuvable : " + zoneDeploiementId));
        restaurant.setZoneDeploiement(zone);
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
        dto.setBannerObjectName(restaurant.getBannerUrl());
        dto.setBannerUrl(minioService.buildPublicFileUrl(restaurant.getBannerUrl()));
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
        dto.setCommissionPourcentage(restaurant.getCommissionPourcentage());
        dto.setVertical(restaurant.getVertical() != null ? restaurant.getVertical().name() : Vertical.RESTAURANT.name());

        StatutRestaurant statut = restaurant.getStatutApprobation() != null
                ? restaurant.getStatutApprobation() : StatutRestaurant.APPROUVE;
        dto.setStatutApprobation(statut.name());
        dto.setMotifRevue(restaurant.getMotifRevue());
        dto.setDateRevue(restaurant.getDateRevue());
        if (restaurant.getJustificatifs() != null) {
            dto.setJustificatifs(new java.util.ArrayList<>(restaurant.getJustificatifs()));
            dto.setJustificatifsUrls(restaurant.getJustificatifs().stream()
                    .map(minioService::buildPublicFileUrl)
                    .collect(Collectors.toList()));
        }

        if (restaurant.getOwner() != null) {
            User owner = restaurant.getOwner();
            dto.setOwnerId(owner.getId());
            dto.setOwnerNom(owner.getNom());
            dto.setOwnerPrenom(owner.getPrenom());
            dto.setOwnerEmail(owner.getEmail());
        }

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

        if (restaurant.getZoneDeploiement() != null) {
            ZoneDeploiement z = restaurant.getZoneDeploiement();
            ZoneDeploiementDTO zoneDTO = new ZoneDeploiementDTO();
            zoneDTO.setId(z.getId());
            zoneDTO.setNom(z.getNom());
            zoneDTO.setDescription(z.getDescription());
            zoneDTO.setCentreLatitude(z.getCentreLatitude());
            zoneDTO.setCentreLongitude(z.getCentreLongitude());
            zoneDTO.setRayonKm(z.getRayonKm());
            zoneDTO.setFraisLivraisonMin(z.getFraisLivraisonMin());
            zoneDTO.setIsActive(z.getIsActive());
            dto.setZoneDeploiement(zoneDTO);
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
