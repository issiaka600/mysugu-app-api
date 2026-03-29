package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.ZoneDeploiementDTO;
import ma.mysuguclientapp.entities.ZoneDeploiement;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.ZoneDeploiementRepository;
import ma.mysuguclientapp.services.interfaces.ZoneDeploiementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoneDeploiementServiceImpl implements ZoneDeploiementService {

    private final ZoneDeploiementRepository zoneDeploiementRepository;
    private final RestaurantRepository restaurantRepository;

    @Override
    @Transactional
    public ZoneDeploiementDTO creerZone(ZoneDeploiementDTO dto) {
        if (zoneDeploiementRepository.existsByNomIgnoreCase(dto.getNom())) {
            throw new BadRequestException("Une zone avec le nom « " + dto.getNom() + " » existe déjà.");
        }

        ZoneDeploiement zone = ZoneDeploiement.builder()
                .nom(dto.getNom().trim())
                .description(dto.getDescription())
                .centreLatitude(dto.getCentreLatitude())
                .centreLongitude(dto.getCentreLongitude())
                .rayonKm(dto.getRayonKm())
                .fraisLivraisonMin(dto.getFraisLivraisonMin())
                .isActive(true)
                .build();

        ZoneDeploiement saved = zoneDeploiementRepository.save(zone);
        log.info("Zone de déploiement créée : {} (rayon {}km)", saved.getNom(), saved.getRayonKm());
        return toDTO(saved);
    }

    @Override
    @Transactional
    public ZoneDeploiementDTO modifierZone(Long id, ZoneDeploiementDTO dto) {
        ZoneDeploiement zone = findById(id);

        // Vérifier l'unicité du nom si changé
        if (!zone.getNom().equalsIgnoreCase(dto.getNom())
                && zoneDeploiementRepository.existsByNomIgnoreCase(dto.getNom())) {
            throw new BadRequestException("Une zone avec le nom « " + dto.getNom() + " » existe déjà.");
        }

        zone.setNom(dto.getNom().trim());
        zone.setDescription(dto.getDescription());
        zone.setCentreLatitude(dto.getCentreLatitude());
        zone.setCentreLongitude(dto.getCentreLongitude());
        zone.setRayonKm(dto.getRayonKm());
        zone.setFraisLivraisonMin(dto.getFraisLivraisonMin());

        return toDTO(zoneDeploiementRepository.save(zone));
    }

    @Override
    @Transactional
    public ZoneDeploiementDTO toggleZone(Long id, boolean actif) {
        ZoneDeploiement zone = findById(id);
        zone.setIsActive(actif);
        return toDTO(zoneDeploiementRepository.save(zone));
    }

    @Override
    @Transactional
    public void supprimerZone(Long id) {
        ZoneDeploiement zone = findById(id);

        long restaurantsLies = restaurantRepository.countByZoneDeploiementId(id);
        if (restaurantsLies > 0) {
            throw new BadRequestException(
                    "Impossible de supprimer la zone « " + zone.getNom() + " » : "
                    + restaurantsLies + " restaurant(s) y sont rattachés. "
                    + "Réaffectez-les à une autre zone avant de supprimer.");
        }

        zoneDeploiementRepository.delete(zone);
        log.info("Zone de déploiement supprimée : {}", zone.getNom());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneDeploiementDTO> getAllZones() {
        return zoneDeploiementRepository.findAll().stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneDeploiementDTO> getZonesActives() {
        return zoneDeploiementRepository.findByIsActiveTrueOrderByNomAsc().stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneDeploiementDTO getZone(Long id) {
        return toDTO(findById(id));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private ZoneDeploiement findById(Long id) {
        return zoneDeploiementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone de déploiement introuvable : " + id));
    }

    public ZoneDeploiementDTO toDTO(ZoneDeploiement z) {
        ZoneDeploiementDTO dto = new ZoneDeploiementDTO();
        dto.setId(z.getId());
        dto.setNom(z.getNom());
        dto.setDescription(z.getDescription());
        dto.setCentreLatitude(z.getCentreLatitude());
        dto.setCentreLongitude(z.getCentreLongitude());
        dto.setRayonKm(z.getRayonKm());
        dto.setFraisLivraisonMin(z.getFraisLivraisonMin());
        dto.setIsActive(z.getIsActive());
        dto.setCreatedAt(z.getCreatedAt());
        return dto;
    }
}
