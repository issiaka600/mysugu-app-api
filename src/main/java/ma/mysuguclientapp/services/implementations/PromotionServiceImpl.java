package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.commerce.PromotionCreateDTO;
import ma.mysuguclientapp.dtos.commerce.PromotionDTO;
import ma.mysuguclientapp.entities.Promotion;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.repositories.PromotionRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.interfaces.PromotionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final RestaurantRepository restaurantRepository;

    @Override
    @Transactional
    public PromotionDTO creerPromotion(PromotionCreateDTO dto) {
        // La FK promotion_id est portée par la table restaurants (côté @ManyToOne).
        // On sauvegarde d'abord la promotion seule, puis on lie via Restaurant.setPromotion().
        Promotion promotion = Promotion.builder()
                .pourcentage(dto.getPourcentage() != null ? dto.getPourcentage() : 0)
                .dateDebut(dto.getDateDebut())
                .dateFin(dto.getDateFin())
                .description(dto.getDescription())
                .isActive(true)
                .code(dto.getCode())
                .montantMinCommande(dto.getMontantMinCommande())
                .usageMax(dto.getUsageMax())
                .usageCount(0)
                .estFlash(dto.getEstFlash() != null ? dto.getEstFlash() : false)
                .build();

        Promotion savedPromotion = promotionRepository.save(promotion);

        boolean tousLesRestaurants = Boolean.TRUE.equals(dto.getAppliquerATousLesRestaurants());

        if (tousLesRestaurants) {
            // Appliquer la promotion à tous les restaurants actifs
            List<Restaurant> tous = restaurantRepository.findByIsActive(true);
            tous.forEach(r -> r.setPromotion(savedPromotion));
            restaurantRepository.saveAll(tous);
            savedPromotion.setRestaurants(tous);
        } else if (dto.getRestaurantId() != null) {
            // Lier à un restaurant spécifique
            Restaurant restaurant = restaurantRepository.findById(dto.getRestaurantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));
            restaurant.setPromotion(savedPromotion);
            restaurantRepository.save(restaurant);
            savedPromotion.setRestaurants(List.of(restaurant));
        }

        return toDTO(savedPromotion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDTO> getAllPromotions() {
        return promotionRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionDTO getPromotion(Long id) {
        return toDTO(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDTO> getPromotionsActives() {
        return promotionRepository.findByIsActiveTrue().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDTO> getPromotionsRestaurant(Long restaurantId) {
        return promotionRepository.findByRestaurantsId(restaurantId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PromotionDTO activerDesactiver(Long id, boolean actif) {
        Promotion promotion = findById(id);
        promotion.setIsActive(actif);
        return toDTO(promotionRepository.save(promotion));
    }

    @Override
    @Transactional
    public void supprimerPromotion(Long id) {
        Promotion promo = findById(id);
        // Dissocier les restaurants avant de supprimer pour éviter la violation de FK
        restaurantRepository.findByPromotion(promo).forEach(r -> {
            r.setPromotion(null);
            restaurantRepository.save(r);
        });
        promotionRepository.delete(promo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDTO> getPromotionsFlash() {
        return promotionRepository.findByEstFlashTrueAndIsActiveTrueAndDateFinAfter(LocalDateTime.now())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    private Promotion findById(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion introuvable: " + id));
    }

    private PromotionDTO toDTO(Promotion p) {
        PromotionDTO dto = new PromotionDTO();
        dto.setId(p.getId());
        dto.setPourcentage(p.getPourcentage());
        dto.setDateDebut(p.getDateDebut());
        dto.setDateFin(p.getDateFin());
        dto.setDescription(p.getDescription());
        dto.setIsActive(p.getIsActive());
        dto.setCode(p.getCode());
        dto.setMontantMinCommande(p.getMontantMinCommande());
        dto.setUsageMax(p.getUsageMax());
        dto.setUsageCount(p.getUsageCount());
        dto.setEstFlash(p.getEstFlash());
        if (p.getRestaurants() != null && p.getRestaurants().size() == 1) {
            dto.setRestaurantId(p.getRestaurants().get(0).getId());
            dto.setRestaurantNom(p.getRestaurants().get(0).getNom());
        }
        return dto;
    }
}
