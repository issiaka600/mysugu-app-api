package ma.mysuguclientapp.services.implementations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ma.mysuguclientapp.dtos.cart.AjouterItemDTO;
import ma.mysuguclientapp.entities.Panier;
import ma.mysuguclientapp.entities.PanierItem;
import ma.mysuguclientapp.entities.PanierItemOption;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.repositories.PanierRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.OptionSelectionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PanierServiceImpl {
    private static final Logger log = LoggerFactory.getLogger(PanierServiceImpl.class);

    private final PanierRepository panierRepository;
    private final PlatRepository platRepository;
    private final UserRepository userRepository;
    private final OptionSelectionService optionSelectionService;

    public PanierServiceImpl(
            PanierRepository panierRepository,
            PlatRepository platRepository,
            UserRepository userRepository,
            OptionSelectionService optionSelectionService) {
        this.panierRepository = panierRepository;
        this.platRepository = platRepository;
        this.userRepository = userRepository;
        this.optionSelectionService = optionSelectionService;
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

        if (!plat.isEffectivementDisponible()) {
            throw new IllegalArgumentException("Ce plat n'est pas disponible");
        }

        // Resolve options selection (throws if constraints violated → 400)
        OptionSelectionService.Selection selection =
                optionSelectionService.resolve(plat, dto.getOptionItemIds());
        BigDecimal prixUnitaireLigne = plat.getPrix().add(selection.getSupplementTotal());
        List<Long> idsChoisis = selection.getItems().stream()
                .map(ma.mysuguclientapp.entities.OptionItem::getId)
                .sorted().collect(Collectors.toList());

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

        // Check if same plat + same option selection already in panier
        PanierItem existant = panier.getItems().stream()
                .filter(it -> it.getPlat().getId().equals(plat.getId()))
                .filter(it -> memeSelection(it, idsChoisis))
                .findFirst().orElse(null);

        if (existant != null) {
            existant.setQuantite(existant.getQuantite() + (dto.getQuantite() != null ? dto.getQuantite() : 1));
            if (dto.getRemarque() != null) existant.setRemarque(dto.getRemarque());
        } else {
            PanierItem item = PanierItem.builder()
                    .panier(panier)
                    .plat(plat)
                    .quantite(dto.getQuantite() != null ? dto.getQuantite() : 1)
                    .prixUnitaire(prixUnitaireLigne)
                    .remarque(dto.getRemarque())
                    .build();
            for (ma.mysuguclientapp.entities.OptionItem oi : selection.getItems()) {
                PanierItemOption snap = new PanierItemOption();
                snap.setPanierItem(item);
                snap.setOptionItemId(oi.getId());
                snap.setOptionGroupNom(oi.getGroup() != null ? oi.getGroup().getNom() : null);
                snap.setOptionNom(oi.getNom());
                snap.setPrixSupplement(oi.getPrixSupplement());
                item.getOptions().add(snap);
            }
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

    private boolean memeSelection(PanierItem item, List<Long> idsChoisis) {
        List<Long> existants = item.getOptions().stream()
                .map(PanierItemOption::getOptionItemId)
                .sorted().collect(Collectors.toList());
        return existants.equals(idsChoisis);
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
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("quantite", item.getQuantite());
        map.put("prixUnitaire", item.getPrixUnitaire());
        map.put("sousTotal", item.getPrixUnitaire().multiply(BigDecimal.valueOf(item.getQuantite())));
        map.put("remarque", item.getRemarque());
        if (item.getPlat() != null) {
            map.put("platId", item.getPlat().getId());
            map.put("platNom", item.getPlat().getNom());
            map.put("platImageUrl", item.getPlat().getImageUrl());
        }
        map.put("options", item.getOptions().stream().map(o -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("optionItemId", o.getOptionItemId());
            m.put("optionGroupNom", o.getOptionGroupNom());
            m.put("optionNom", o.getOptionNom());
            m.put("prixSupplement", o.getPrixSupplement());
            return m;
        }).collect(Collectors.toList()));
        return map;
    }
}
