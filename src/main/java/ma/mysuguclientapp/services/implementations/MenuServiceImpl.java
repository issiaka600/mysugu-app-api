package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.restaurant.MenuCreateDTO;
import ma.mysuguclientapp.dtos.restaurant.MenuDTO;
import ma.mysuguclientapp.entities.Menu;
import ma.mysuguclientapp.entities.MenuPlat;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.repositories.MenuRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuServiceImpl {

    private final MenuRepository menuRepository;
    private final RestaurantRepository restaurantRepository;
    private final PlatRepository platRepository;

    @Transactional
    public MenuDTO creerMenu(MenuCreateDTO dto) {
        Restaurant restaurant = restaurantRepository.findById(dto.getRestaurantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));

        Menu menu = Menu.builder()
                .restaurant(restaurant)
                .nom(dto.getNom())
                .description(dto.getDescription())
                .heureDebut(dto.getHeureDebut())
                .heureFin(dto.getHeureFin())
                .joursSemaine(dto.getJoursSemaine())
                .isActive(true)
                .menuPlats(new ArrayList<>())
                .build();

        if (dto.getPlats() != null) {
            for (MenuCreateDTO.MenuPlatDTO platDTO : dto.getPlats()) {
                Plat plat = platRepository.findById(platDTO.getPlatId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plat introuvable: " + platDTO.getPlatId()));
                MenuPlat mp = MenuPlat.builder()
                        .menu(menu)
                        .plat(plat)
                        .prixSpecial(platDTO.getPrixSpecial())
                        .ordreAffichage(platDTO.getOrdreAffichage() != null ? platDTO.getOrdreAffichage() : 0)
                        .build();
                menu.getMenuPlats().add(mp);
            }
        }

        return toDTO(menuRepository.save(menu));
    }

    @Transactional(readOnly = true)
    public MenuDTO getMenu(Long id) {
        return toDTO(findById(id));
    }

    @Transactional(readOnly = true)
    public List<MenuDTO> getMenusRestaurant(Long restaurantId) {
        return menuRepository.findByRestaurantIdAndIsActiveTrueOrderByNomAsc(restaurantId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public MenuDTO activerDesactiver(Long id, boolean actif) {
        Menu menu = findById(id);
        menu.setIsActive(actif);
        return toDTO(menuRepository.save(menu));
    }

    @Transactional
    public void supprimerMenu(Long id) {
        menuRepository.delete(findById(id));
    }

    private Menu findById(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu introuvable: " + id));
    }

    private MenuDTO toDTO(Menu menu) {
        MenuDTO dto = new MenuDTO();
        dto.setId(menu.getId());
        dto.setNom(menu.getNom());
        dto.setDescription(menu.getDescription());
        dto.setHeureDebut(menu.getHeureDebut());
        dto.setHeureFin(menu.getHeureFin());
        dto.setJoursSemaine(menu.getJoursSemaine());
        dto.setIsActive(menu.getIsActive());
        if (menu.getRestaurant() != null) {
            dto.setRestaurantId(menu.getRestaurant().getId());
            dto.setRestaurantNom(menu.getRestaurant().getNom());
        }
        dto.setPlats(menu.getMenuPlats().stream().map(mp -> {
            MenuDTO.MenuPlatItemDTO item = new MenuDTO.MenuPlatItemDTO();
            item.setMenuPlatId(mp.getId());
            item.setOrdreAffichage(mp.getOrdreAffichage());
            item.setPrixSpecial(mp.getPrixSpecial());
            if (mp.getPlat() != null) {
                item.setPlatId(mp.getPlat().getId());
                item.setPlatNom(mp.getPlat().getNom());
                item.setPrixOriginal(mp.getPlat().getPrix());
                item.setPrixEffectif(mp.getPrixSpecial() != null ? mp.getPrixSpecial() : mp.getPlat().getPrix());
            }
            return item;
        }).collect(Collectors.toList()));
        return dto;
    }
}
