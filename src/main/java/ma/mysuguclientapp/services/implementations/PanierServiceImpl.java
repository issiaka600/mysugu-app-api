package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.cart.AjouterItemDTO;
import ma.mysuguclientapp.dtos.cart.PanierDTO;
import ma.mysuguclientapp.dtos.cart.PanierItemDTO;
import ma.mysuguclientapp.entities.Panier;
import ma.mysuguclientapp.entities.PanierItem;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.repositories.PanierRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PanierServiceImpl {

    private final PanierRepository panierRepository;
    private final PlatRepository platRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PanierDTO getPanier(Long userId) {
        Panier panier = panierRepository.findByUserId(userId).orElse(null);
        if (panier == null) return emptyPanier();
        return toDTO(panier);
    }

    @Transactional
    public PanierDTO ajouterItem(Long userId, AjouterItemDTO dto) {
        Plat plat = platRepository.findById(dto.getPlatId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plat introuvable"));

        if (!Boolean.TRUE.equals(plat.getIsAvailable())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce plat n'est pas disponible");
        }

        Panier panier = panierRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
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
    public PanierDTO modifierQuantite(Long userId, Long itemId, Integer quantite) {
        Panier panier = getPanierOrThrow(userId);
        PanierItem item = panier.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item introuvable"));

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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Panier introuvable"));
    }

    private PanierDTO emptyPanier() {
        PanierDTO dto = new PanierDTO();
        dto.setMontantTotal(BigDecimal.ZERO);
        dto.setNombreItems(0);
        return dto;
    }

    private PanierDTO toDTO(Panier panier) {
        PanierDTO dto = new PanierDTO();
        dto.setId(panier.getId());
        dto.setMontantTotal(panier.getMontantTotal());
        dto.setUpdatedAt(panier.getUpdatedAt());
        if (panier.getRestaurant() != null) {
            dto.setRestaurantId(panier.getRestaurant().getId());
            dto.setRestaurantNom(panier.getRestaurant().getNom());
        }
        dto.setItems(panier.getItems().stream().map(this::toItemDTO).collect(Collectors.toList()));
        dto.setNombreItems(panier.getItems().stream().mapToInt(PanierItem::getQuantite).sum());
        return dto;
    }

    private PanierItemDTO toItemDTO(PanierItem item) {
        PanierItemDTO dto = new PanierItemDTO();
        dto.setId(item.getId());
        dto.setQuantite(item.getQuantite());
        dto.setPrixUnitaire(item.getPrixUnitaire());
        dto.setSousTotal(item.getPrixUnitaire().multiply(BigDecimal.valueOf(item.getQuantite())));
        dto.setRemarque(item.getRemarque());
        if (item.getPlat() != null) {
            dto.setPlatId(item.getPlat().getId());
            dto.setPlatNom(item.getPlat().getNom());
            dto.setPlatImageUrl(item.getPlat().getImageUrl());
        }
        return dto;
    }
}
