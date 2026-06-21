package ma.mysuguclientapp.services.implementations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ma.mysuguclientapp.dtos.cart.AjouterItemDTO;
import ma.mysuguclientapp.entities.Panier;
import ma.mysuguclientapp.entities.PanierItem;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.repositories.PanierRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PanierServiceImpl {
    private static final Logger log = LoggerFactory.getLogger(PanierServiceImpl.class);

    private final PanierRepository panierRepository;
    private final PlatRepository platRepository;
    private final UserRepository userRepository;

    public PanierServiceImpl(
            PanierRepository panierRepository,
            PlatRepository platRepository,
            UserRepository userRepository) {
        this.panierRepository = panierRepository;
        this.platRepository = platRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPanier(Long userId) {
        Panier panier = panierRepository.findByUserId(userId).orElse(null);
        if (panier == null) return emptyPanier();
        return toDTO(panier);
    }

    @Transactional
    public Map<String, Object> ajouterItem(Long userId, AjouterItemDTO dto) {
        Plat plat = platRepository.findById(dto.getPlatId())
                .orElseThrow(() -> new IllegalArgumentException("Plat introuvable"));

        if (!Boolean.TRUE.equals(plat.getIsAvailable())) {
            throw new IllegalArgumentException("Ce plat n'est pas disponible");
        }

        Panier panier = panierRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
            return Panier.builder().user(user).montantTotal(BigDecimal.ZERO).build();
        });

        // If panier has items from a different restaurant, clear it
        if (panier.getRestaurant() != null && !panier.getRestaurant().getId().equals(plat.getRestaurant().getId())) {
            panier.getItems().clear();
            panier.setMontantTotal(BigDecimal.ZERO);
        }

        panier.setRestaurant(plat.getRestaurant());

        // Check if item already in panier
        PanierItem existingItem = panier.getItems().stream()
                .filter(i -> i.getPlat().getId().equals(dto.getPlatId()))
                .findFirst().orElse(null);

        if (existingItem != null) {
            existingItem.setQuantite(existingItem.getQuantite() + dto.getQuantite());
            if (dto.getRemarque() != null) existingItem.setRemarque(dto.getRemarque());
        } else {
            PanierItem item = PanierItem.builder()
                    .panier(panier)
                    .plat(plat)
                    .quantite(dto.getQuantite())
                    .prixUnitaire(plat.getPrix())
                    .remarque(dto.getRemarque())
                    .build();
            panier.getItems().add(item);
        }

        recalculerTotal(panier);
        return toDTO(panierRepository.save(panier));
    }

    @Transactional
    public Map<String, Object> modifierQuantite(Long userId, Long itemId, Integer quantite) {
        Panier panier = getPanierOrThrow(userId);
        PanierItem item = panier.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item introuvable"));

        if (quantite <= 0) {
            panier.getItems().remove(item);
        } else {
            item.setQuantite(quantite);
        }

        recalculerTotal(panier);
        if (panier.getItems().isEmpty()) {
            panier.setRestaurant(null);
        }
        return toDTO(panierRepository.save(panier));
    }

    @Transactional
    public void viderPanier(Long userId) {
        panierRepository.findByUserId(userId).ifPresent(panier -> {
            panier.getItems().clear();
            panier.setRestaurant(null);
            panier.setMontantTotal(BigDecimal.ZERO);
            panierRepository.save(panier);
        });
    }

    private void recalculerTotal(Panier panier) {
        BigDecimal total = panier.getItems().stream()
                .map(item -> item.getPrixUnitaire().multiply(BigDecimal.valueOf(item.getQuantite())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        panier.setMontantTotal(total);
    }

    private Panier getPanierOrThrow(Long userId) {
        return panierRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Panier introuvable"));
    }

    private Map<String, Object> emptyPanier() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", null);
        dto.put("restaurantId", null);
        dto.put("restaurantNom", null);
        dto.put("items", Collections.emptyList());
        dto.put("montantTotal", BigDecimal.ZERO);
        dto.put("nombreItems", 0);
        dto.put("updatedAt", null);
        return dto;
    }

    private Map<String, Object> toDTO(Panier panier) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", panier.getId());
        dto.put("montantTotal", panier.getMontantTotal());
        dto.put("updatedAt", panier.getUpdatedAt());
        if (panier.getRestaurant() != null) {
            dto.put("restaurantId", panier.getRestaurant().getId());
            dto.put("restaurantNom", panier.getRestaurant().getNom());
        } else {
            dto.put("restaurantId", null);
            dto.put("restaurantNom", null);
        }
        dto.put("items", panier.getItems().stream()
                .filter(item -> item != null && item.getPlat() != null && item.getQuantite() != null)
                .map(this::toItemDTO)
                .collect(Collectors.toList()));
        dto.put("nombreItems", panier.getItems().stream()
                .filter(item -> item != null && item.getQuantite() != null)
                .mapToInt(PanierItem::getQuantite)
                .sum());
        return dto;
    }

    private Map<String, Object> toItemDTO(PanierItem item) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", item.getId());
        dto.put("quantite", item.getQuantite());
        dto.put("prixUnitaire", item.getPrixUnitaire());
        dto.put("sousTotal", item.getPrixUnitaire().multiply(BigDecimal.valueOf(item.getQuantite())));
        dto.put("remarque", item.getRemarque());
        if (item.getPlat() != null) {
            dto.put("platId", item.getPlat().getId());
            dto.put("platNom", item.getPlat().getNom());
            dto.put("platImageUrl", item.getPlat().getImageUrl());
        }
        return dto;
    }
}
