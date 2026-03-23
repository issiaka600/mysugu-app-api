package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.restaurant.AjouterEmployeDTO;
import ma.mysuguclientapp.dtos.restaurant.RestaurantEmployeDTO;
import ma.mysuguclientapp.services.implementations.RestaurantEmployeServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/employes")
@RequiredArgsConstructor
public class RestaurantEmployeController {

    private final RestaurantEmployeServiceImpl employeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public ResponseEntity<List<RestaurantEmployeDTO>> getEmployes(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(employeService.getEmployes(restaurantId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public ResponseEntity<RestaurantEmployeDTO> ajouterEmploye(@PathVariable Long restaurantId,
                                                                @RequestBody AjouterEmployeDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeService.ajouterEmploye(restaurantId, dto));
    }

    @DeleteMapping("/{employeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public ResponseEntity<Void> retirerEmploye(@PathVariable Long restaurantId,
                                                @PathVariable Long employeId) {
        employeService.retirerEmploye(restaurantId, employeId);
        return ResponseEntity.noContent().build();
    }
}
