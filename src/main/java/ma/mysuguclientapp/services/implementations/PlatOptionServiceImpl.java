package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.OptionGroupDTO;
import ma.mysuguclientapp.dtos.OptionItemDTO;
import ma.mysuguclientapp.entities.OptionGroup;
import ma.mysuguclientapp.entities.OptionItem;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.OptionSelectionMode;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.PlatOptionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PlatOptionServiceImpl implements PlatOptionService {

    private final PlatRepository platRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<OptionGroupDTO> getOptions(Long platId) {
        Plat plat = platRepository.findById(platId)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé"));
        return plat.getOptionGroups().stream().map(this::toGroupDTO).collect(Collectors.toList());
    }

    @Override
    public List<OptionGroupDTO> replaceOptions(Long platId, List<OptionGroupDTO> groups, String userEmail) {
        Plat plat = platRepository.findById(platId)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé"));
        assertCanManage(plat, userEmail);

        // replace-all : on vide la collection (orphanRemoval supprime les anciens groupes/items)
        plat.getOptionGroups().clear();
        if (groups != null) {
            int gOrdre = 0;
            for (OptionGroupDTO gd : groups) {
                OptionGroup g = new OptionGroup();
                g.setPlat(plat);
                g.setNom(requireText(gd.getNom(), "Le nom de la section est obligatoire"));
                g.setSelectionMode(parseMode(gd.getSelectionMode()));
                g.setObligatoire(Boolean.TRUE.equals(gd.getObligatoire()));
                g.setMinSelections(gd.getMinSelections() != null ? gd.getMinSelections() : 0);
                g.setMaxSelections(gd.getMaxSelections());
                g.setOrdre(gd.getOrdre() != null ? gd.getOrdre() : gOrdre);
                normaliserEtValider(g);

                int iOrdre = 0;
                if (gd.getItems() != null) {
                    for (OptionItemDTO it : gd.getItems()) {
                        OptionItem item = new OptionItem();
                        item.setGroup(g);
                        item.setNom(requireText(it.getNom(), "Le nom d'un choix est obligatoire"));
                        BigDecimal prix = it.getPrixSupplement() != null ? it.getPrixSupplement() : BigDecimal.ZERO;
                        if (prix.compareTo(BigDecimal.ZERO) < 0) {
                            throw new BadRequestException("Le supplément de prix ne peut pas être négatif");
                        }
                        item.setPrixSupplement(prix);
                        item.setDisponible(it.getDisponible() == null || it.getDisponible());
                        item.setOrdre(it.getOrdre() != null ? it.getOrdre() : iOrdre);
                        g.getItems().add(item);
                        iOrdre++;
                    }
                }
                plat.getOptionGroups().add(g);
                gOrdre++;
            }
        }
        Plat saved = platRepository.save(plat);
        return saved.getOptionGroups().stream().map(this::toGroupDTO).collect(Collectors.toList());
    }

    private void assertCanManage(Plat plat, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AccessDeniedException("Utilisateur non authentifié"));
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }
        boolean estProprietaire = plat.getRestaurant() != null
                && plat.getRestaurant().getOwner() != null
                && plat.getRestaurant().getOwner().getId().equals(user.getId());
        if (!estProprietaire) {
            throw new AccessDeniedException("Vous ne gérez pas ce plat");
        }
    }

    private void normaliserEtValider(OptionGroup g) {
        if (g.getSelectionMode() == OptionSelectionMode.SINGLE) {
            g.setMaxSelections(1);
        }
        if (Boolean.TRUE.equals(g.getObligatoire()) && g.getMinSelections() < 1) {
            g.setMinSelections(1);
        }
        if (g.getMinSelections() < 0) {
            throw new BadRequestException("minSelections ne peut pas être négatif");
        }
        if (g.getMaxSelections() != null && g.getMinSelections() > g.getMaxSelections()) {
            throw new BadRequestException("minSelections ne peut pas dépasser maxSelections");
        }
    }

    private OptionSelectionMode parseMode(String s) {
        if (s == null) return OptionSelectionMode.SINGLE;
        try { return OptionSelectionMode.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { throw new BadRequestException("Mode de sélection invalide: " + s); }
    }

    private String requireText(String s, String msg) {
        if (s == null || s.isBlank()) throw new BadRequestException(msg);
        return s.trim();
    }

    private OptionGroupDTO toGroupDTO(OptionGroup g) {
        OptionGroupDTO dto = new OptionGroupDTO();
        dto.setId(g.getId());
        dto.setNom(g.getNom());
        dto.setSelectionMode(g.getSelectionMode() != null ? g.getSelectionMode().name() : null);
        dto.setObligatoire(g.getObligatoire());
        dto.setMinSelections(g.getMinSelections());
        dto.setMaxSelections(g.getMaxSelections());
        dto.setOrdre(g.getOrdre());
        List<OptionItemDTO> items = new ArrayList<>();
        if (g.getItems() != null) {
            for (OptionItem it : g.getItems()) {
                OptionItemDTO id = new OptionItemDTO();
                id.setId(it.getId());
                id.setNom(it.getNom());
                id.setPrixSupplement(it.getPrixSupplement());
                id.setDisponible(it.getDisponible());
                id.setOrdre(it.getOrdre());
                items.add(id);
            }
        }
        dto.setItems(items);
        return dto;
    }
}
