package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.restaurant.ZoneLivraisonDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.ZoneLivraison;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.ZoneLivraisonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoneLivraisonServiceImpl {

    private final ZoneLivraisonRepository zoneLivraisonRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public ZoneLivraisonDTO creerZone(ZoneLivraisonDTO dto) {
        Restaurant restaurant = restaurantRepository.findById(dto.getRestaurantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));

        ZoneLivraison zone = ZoneLivraison.builder()
                .restaurant(restaurant)
                .nom(dto.getNom())
                .rayonKm(dto.getRayonKm())
                .fraisLivraison(dto.getFraisLivraison())
                .montantMinCommande(dto.getMontantMinCommande())
                .tempsEstimeMinutes(dto.getTempsEstimeMinutes())
                .centreLatitude(dto.getCentreLatitude())
                .centreLongitude(dto.getCentreLongitude())
                .isActive(true)
                .build();

        return toDTO(zoneLivraisonRepository.save(zone));
    }

    @Transactional(readOnly = true)
    public List<ZoneLivraisonDTO> getZonesRestaurant(Long restaurantId) {
        return zoneLivraisonRepository.findByRestaurantIdAndIsActiveTrueOrderByFraisLivraisonAsc(restaurantId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public ZoneLivraisonDTO modifierZone(Long id, ZoneLivraisonDTO dto) {
        ZoneLivraison zone = findById(id);
        zone.setNom(dto.getNom());
        zone.setRayonKm(dto.getRayonKm());
        zone.setFraisLivraison(dto.getFraisLivraison());
        zone.setMontantMinCommande(dto.getMontantMinCommande());
        zone.setTempsEstimeMinutes(dto.getTempsEstimeMinutes());
        zone.setCentreLatitude(dto.getCentreLatitude());
        zone.setCentreLongitude(dto.getCentreLongitude());
        return toDTO(zoneLivraisonRepository.save(zone));
    }

    @Transactional
    public void supprimerZone(Long id) {
        zoneLivraisonRepository.delete(findById(id));
    }

    private ZoneLivraison findById(Long id) {
        return zoneLivraisonRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone introuvable: " + id));
    }

    private ZoneLivraisonDTO toDTO(ZoneLivraison z) {
        ZoneLivraisonDTO dto = new ZoneLivraisonDTO();
        dto.setId(z.getId());
        dto.setNom(z.getNom());
        dto.setRayonKm(z.getRayonKm());
        dto.setFraisLivraison(z.getFraisLivraison());
        dto.setMontantMinCommande(z.getMontantMinCommande());
        dto.setTempsEstimeMinutes(z.getTempsEstimeMinutes());
        dto.setIsActive(z.getIsActive());
        dto.setCentreLatitude(z.getCentreLatitude());
        dto.setCentreLongitude(z.getCentreLongitude());
        if (z.getRestaurant() != null) dto.setRestaurantId(z.getRestaurant().getId());
        return dto;
    }
}
