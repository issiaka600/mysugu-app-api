package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.PlatAvailabilityUpdateDTO;
import ma.mysuguclientapp.dtos.PlatCreateDTO;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.CategoriePlatDef;
import ma.mysuguclientapp.enumerations.ModeDisponibilitePlat;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.CategoriePlatDefRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.interfaces.PlatService;
import ma.mysuguclientapp.services.interfaces.TopVenteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PlatServiceImpl implements PlatService {
    private final PlatRepository platRepository;
    private final RestaurantRepository restaurantRepository;
    private final MinioService minioService;
    private final CategoriePlatDefRepository categoriePlatDefRepository;
    private final TopVenteService topVenteService;

    @Override
    @Transactional(readOnly = true)
    public Page<PlatDTO> getAllPlats(Long restaurantId, String categorie, String categorieProduit,
                                     Boolean available, Boolean topVente, String vertical, Pageable pageable) {
        String categoriePlat = parseCategorie(categorie);
        Vertical v = "ALL".equalsIgnoreCase(vertical) ? null : parseVertical(vertical);

        Page<Plat> page = platRepository.rechercheFiltree(
                restaurantId, categoriePlat, categorieProduit, v, pageable);

        // Le filtre `available` porte sur la disponibilité EFFECTIVE (flag vendeur pondéré par le
        // stock + expiration d'une indisponibilité temporaire), pas expressible en SQL seul : il
        // reste donc appliqué ici, en mémoire, après la requête paginée. Conséquence assumée : le
        // total de pagination (page.getTotalElements()) porte sur la requête SQL, avant ce filtre.
        // Idem pour `topVente` : le statut est EFFECTIF (flag manuel OU ventes livrées ≥ seuil),
        // calculé ici en mémoire à partir des ventes cumulées.
        Map<Long, Long> ventes = topVenteService.ventesParPlatLivrees();
        List<PlatDTO> contenu = page.getContent().stream()
                .map(this::refreshAvailabilityIfNeeded)
                .filter(plat -> available == null || plat.isEffectivementDisponible() == available)
                .filter(plat -> topVente == null
                        || topVenteService.estTopVente(plat.getId(), plat.getTopVente(), ventes) == topVente)
                .map(plat -> convertToDTO(plat, ventes))
                .toList();

        return new PageImpl<>(contenu, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PlatDTO getPlatById(Long id) {
        Plat plat = platRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé avec l'ID: " + id));
        return convertToDTO(refreshAvailabilityIfNeeded(plat), topVenteService.ventesParPlatLivrees());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatDTO> getPlatsByRestaurant(Long restaurantId) {
        Map<Long, Long> ventes = topVenteService.ventesParPlatLivrees();
        return platRepository.findByRestaurantId(restaurantId).stream()
                .map(this::refreshAvailabilityIfNeeded)
                .filter(Plat::isEffectivementDisponible)
                .map(plat -> convertToDTO(plat, ventes))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatDTO> searchPlats(String keyword, String vertical) {
        Vertical v = "ALL".equalsIgnoreCase(vertical) ? null : parseVertical(vertical);
        Map<Long, Long> ventes = topVenteService.ventesParPlatLivrees();
        return platRepository.searchByKeywordAndVertical(keyword, v).stream()
                .map(this::refreshAvailabilityIfNeeded)
                .map(plat -> convertToDTO(plat, ventes))
                .collect(Collectors.toList());
    }

    /** Convention partagée (voir {@link RestaurantServiceImpl#parseVertical}) : absent ⇒ RESTAURANT, inconnue ⇒ 400. */
    private Vertical parseVertical(String value) {
        return RestaurantServiceImpl.parseVertical(value);
    }

    @Override
    public PlatDTO createPlat(PlatCreateDTO platDTO, MultipartFile image) {
        log.info("PLAT CREATE DTO : {}", platDTO);
        Restaurant restaurant = restaurantRepository.findById(platDTO.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));

        Plat plat = new Plat();
        plat.setNom(platDTO.getNom());
        plat.setDescription(platDTO.getDescription());
        plat.setPrix(platDTO.getPrix());
        plat.setRestaurant(restaurant);
        plat.setTempsPreparation(platDTO.getTempsPreparation());
        plat.setQuantiteStock(platDTO.getQuantiteStock());
        plat.setSeuilAlerteStock(platDTO.getSeuilAlerteStock());
        plat.setTopVente(Boolean.TRUE.equals(platDTO.getTopVente()));

        if (platDTO.getCategoriePlat() != null) {
            plat.setCategoriePlat(parseCategorie(platDTO.getCategoriePlat()));
        }
        plat.setCategorieProduit(platDTO.getCategorieProduit());

        if (platDTO.getIngredients() != null) {
            plat.setIngredients(platDTO.getIngredients());
        }

        if (Boolean.TRUE.equals(platDTO.getRemoveImage())) {
            plat.setImageUrl(null);
        }

        applyAvailabilityMode(plat, parseAvailabilityMode(platDTO.getAvailabilityMode()), platDTO.getIndisponibleJusqua());

        if (image != null && !image.isEmpty()) {
            try {
                plat.setImageUrl(minioService.uploadFile(image, "plats"));
            } catch (Exception e) {
                log.error("Erreur lors de l'upload de l'image", e);
                throw new BadRequestException("Erreur lors de l'upload de l'image");
            }
        }

        Plat savedPlat = platRepository.save(plat);
        log.info("Plat créé: {}", savedPlat.getNom());
        return convertToDTO(savedPlat, topVenteService.ventesParPlatLivrees());
    }

    @Override
    public PlatDTO updatePlat(Long id, PlatCreateDTO platDTO, MultipartFile image) {
        Plat plat = platRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé"));

        if (platDTO != null) {
            plat.setNom(platDTO.getNom());
            plat.setDescription(platDTO.getDescription());
            plat.setPrix(platDTO.getPrix());
            plat.setTempsPreparation(platDTO.getTempsPreparation());
            if (platDTO.getQuantiteStock() != null) {
                plat.setQuantiteStock(platDTO.getQuantiteStock());
            }
            if (platDTO.getSeuilAlerteStock() != null) {
                plat.setSeuilAlerteStock(platDTO.getSeuilAlerteStock());
            }
            if (platDTO.getTopVente() != null) {
                plat.setTopVente(platDTO.getTopVente());
            }

            if (platDTO.getCategoriePlat() != null) {
                plat.setCategoriePlat(parseCategorie(platDTO.getCategoriePlat()));
            }
            plat.setCategorieProduit(platDTO.getCategorieProduit());
            if (platDTO.getIngredients() != null) {
                plat.setIngredients(platDTO.getIngredients());
            }

            if (Boolean.TRUE.equals(platDTO.getRemoveImage()) && plat.getImageUrl() != null) {
                try {
                    minioService.deleteFile(plat.getImageUrl());
                    plat.setImageUrl(null);
                } catch (Exception e) {
                    log.error("Erreur lors de la suppression de l'image", e);
                    throw new BadRequestException("Erreur lors de la suppression de l'image");
                }
            }

            if (platDTO.getAvailabilityMode() != null || platDTO.getIndisponibleJusqua() != null) {
                applyAvailabilityMode(plat, parseAvailabilityMode(platDTO.getAvailabilityMode()), platDTO.getIndisponibleJusqua());
            }
        }

        if (image != null && !image.isEmpty()) {
            try {
                if (plat.getImageUrl() != null) {
                    minioService.deleteFile(plat.getImageUrl());
                }
                plat.setImageUrl(minioService.uploadFile(image, "plats"));
            } catch (Exception e) {
                log.error("Erreur lors de l'upload de l'image", e);
                throw new BadRequestException("Erreur lors de l'upload de l'image");
            }
        }

        Plat updatedPlat = platRepository.save(plat);
        log.info("Plat mis à jour: {}", updatedPlat.getNom());
        return convertToDTO(updatedPlat, topVenteService.ventesParPlatLivrees());
    }

    @Override
    @Transactional
    public PlatDTO updateIngredients(Long id, List<String> ingredients) {
        Plat plat = platRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé"));
        List<String> nettoyes = ingredients == null ? List.of() : ingredients.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        plat.setIngredients(nettoyes);
        log.info("Ingrédients du plat {} mis à jour ({} élément(s))", plat.getNom(), nettoyes.size());
        return convertToDTO(platRepository.save(plat), topVenteService.ventesParPlatLivrees());
    }

    @Override
    @Transactional
    public PlatDTO updateTopVente(Long id, Boolean topVente) {
        Plat plat = platRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé"));
        plat.setTopVente(Boolean.TRUE.equals(topVente));
        log.info("Flag manuel « Top des ventes » du plat {} → {}", plat.getNom(), plat.getTopVente());
        return convertToDTO(platRepository.save(plat), topVenteService.ventesParPlatLivrees());
    }

    @Override
    public void deletePlat(Long id) {
        Plat plat = platRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé"));

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
    public PlatDTO updateAvailability(Long id, PlatAvailabilityUpdateDTO availabilityDTO) {
        Plat plat = platRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé"));

        if (availabilityDTO == null || availabilityDTO.getAvailabilityMode() == null || availabilityDTO.getAvailabilityMode().isBlank()) {
            ModeDisponibilitePlat targetMode = plat.getIsAvailable()
                    ? ModeDisponibilitePlat.INDISPONIBLE_DEFINITIVE
                    : ModeDisponibilitePlat.DISPONIBLE;
            applyAvailabilityMode(plat, targetMode, null);
        } else {
            applyAvailabilityMode(plat, parseAvailabilityMode(availabilityDTO.getAvailabilityMode()), null);
        }

        Plat updatedPlat = platRepository.save(plat);
        log.info("Disponibilité du plat {} changée à: {}", plat.getNom(), plat.getAvailabilityMode());
        return convertToDTO(updatedPlat, topVenteService.ventesParPlatLivrees());
    }

    private void applyAvailabilityMode(Plat plat, ModeDisponibilitePlat mode, LocalDateTime indisponibleJusqua) {
        ModeDisponibilitePlat resolvedMode = mode == null ? ModeDisponibilitePlat.DISPONIBLE : mode;
        plat.setAvailabilityMode(resolvedMode);

        switch (resolvedMode) {
            case DISPONIBLE -> {
                plat.setIsAvailable(true);
                plat.setIndisponibleJusqua(null);
            }
            case INDISPONIBLE_TEMPORAIRE -> {
                plat.setIsAvailable(false);
                plat.setIndisponibleJusqua(indisponibleJusqua != null ? indisponibleJusqua : LocalDateTime.now().plusHours(24));
            }
            case INDISPONIBLE_DEFINITIVE -> {
                plat.setIsAvailable(false);
                plat.setIndisponibleJusqua(null);
            }
        }
    }

    private Plat refreshAvailabilityIfNeeded(Plat plat) {
        if (plat.getAvailabilityMode() == ModeDisponibilitePlat.INDISPONIBLE_TEMPORAIRE
                && plat.getIndisponibleJusqua() != null
                && plat.getIndisponibleJusqua().isBefore(LocalDateTime.now())) {
            plat.setAvailabilityMode(ModeDisponibilitePlat.DISPONIBLE);
            plat.setIndisponibleJusqua(null);
            plat.setIsAvailable(true);
            return platRepository.save(plat);
        }
        return plat;
    }

    private ModeDisponibilitePlat parseAvailabilityMode(String value) {
        if (value == null || value.isBlank()) {
            return ModeDisponibilitePlat.DISPONIBLE;
        }

        try {
            return ModeDisponibilitePlat.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Mode de disponibilité invalide: " + value);
        }
    }

    /**
     * Valide et normalise un code de catégorie de plat. Les valeurs sont vérifiées contre la
     * table {@code categorie_plat_defs} (dashboard) — plus d'enum figée.
     */
    private String parseCategorie(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String code = value.trim().toUpperCase();
        if (!categoriePlatDefRepository.existsByCodeIgnoreCase(code)) {
            throw new BadRequestException("Catégorie de plat inconnue: " + value);
        }
        return code;
    }

    private PlatDTO convertToDTO(Plat plat, Map<Long, Long> ventes) {
        PlatDTO dto = new PlatDTO();
        dto.setId(plat.getId());
        dto.setNom(plat.getNom());
        dto.setDescription(plat.getDescription());
        dto.setPrix(plat.getPrix());
        dto.setImageObjectName(plat.getImageUrl());
        dto.setImageUrl(minioService.buildPublicFileUrl(plat.getImageUrl()));
        dto.setIngredients(plat.getIngredients());
        // Disponibilité effective : le client ne doit jamais se voir proposer un produit en rupture.
        dto.setIsAvailable(plat.isEffectivementDisponible());
        dto.setQuantiteStock(plat.getQuantiteStock());
        dto.setStockGere(plat.getQuantiteStock() != null);
        dto.setAlerteStockBas(plat.getQuantiteStock() != null
                && plat.getSeuilAlerteStock() != null
                && plat.getQuantiteStock() <= plat.getSeuilAlerteStock());
        dto.setTopVente(topVenteService.estTopVente(plat.getId(), plat.getTopVente(), ventes));
        dto.setTempsPreparation(plat.getTempsPreparation());
        dto.setIndisponibleJusqua(plat.getIndisponibleJusqua());

        if (plat.getAvailabilityMode() != null) {
            dto.setAvailabilityMode(plat.getAvailabilityMode().name());
        }
        if (plat.getCategoriePlat() != null) {
            String code = plat.getCategoriePlat();
            dto.setCategoriePlat(code);
            CategoriePlatDef def = categoriePlatDefRepository.findByCodeIgnoreCase(code).orElse(null);
            dto.setCategoriePlatLabel(def != null ? def.getLibelle() : code);
            dto.setCategoriePlatOrdre(def != null ? def.getOrdre() : null);
            dto.setCategoriePlatIcone(def != null ? def.getIcone() : null);
        }
        dto.setCategorieProduit(plat.getCategorieProduit());
        if (plat.getRestaurant() != null) {
            dto.setRestaurantId(plat.getRestaurant().getId());
            dto.setRestaurantNom(plat.getRestaurant().getNom());
        }

        if (plat.getOptionGroups() != null) {
            dto.setOptionGroups(plat.getOptionGroups().stream().map(g -> {
                ma.mysuguclientapp.dtos.OptionGroupDTO gd = new ma.mysuguclientapp.dtos.OptionGroupDTO();
                gd.setId(g.getId());
                gd.setNom(g.getNom());
                gd.setSelectionMode(g.getSelectionMode() != null ? g.getSelectionMode().name() : null);
                gd.setObligatoire(g.getObligatoire());
                gd.setMinSelections(g.getMinSelections());
                gd.setMaxSelections(g.getMaxSelections());
                gd.setOrdre(g.getOrdre());
                gd.setItems(g.getItems() == null ? java.util.List.of() : g.getItems().stream().map(it -> {
                    ma.mysuguclientapp.dtos.OptionItemDTO id = new ma.mysuguclientapp.dtos.OptionItemDTO();
                    id.setId(it.getId());
                    id.setNom(it.getNom());
                    id.setPrixSupplement(it.getPrixSupplement());
                    id.setDisponible(it.getDisponible());
                    id.setOrdre(it.getOrdre());
                    return id;
                }).collect(java.util.stream.Collectors.toList()));
                return gd;
            }).collect(java.util.stream.Collectors.toList()));
        }

        return dto;
    }
}
