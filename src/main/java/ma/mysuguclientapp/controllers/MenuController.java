package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.restaurant.MenuCreateDTO;
import ma.mysuguclientapp.dtos.restaurant.MenuDTO;
import ma.mysuguclientapp.services.implementations.MenuServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuServiceImpl menuService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public ResponseEntity<MenuDTO> creerMenu(@RequestBody MenuCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.creerMenu(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuDTO> getMenu(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.getMenu(id));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<MenuDTO>> getMenusRestaurant(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(menuService.getMenusRestaurant(restaurantId));
    }

    @PatchMapping("/{id}/activer")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public ResponseEntity<MenuDTO> activerDesactiver(@PathVariable Long id, @RequestParam boolean actif) {
        return ResponseEntity.ok(menuService.activerDesactiver(id, actif));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public ResponseEntity<Void> supprimerMenu(@PathVariable Long id) {
        menuService.supprimerMenu(id);
        return ResponseEntity.noContent().build();
    }
}
