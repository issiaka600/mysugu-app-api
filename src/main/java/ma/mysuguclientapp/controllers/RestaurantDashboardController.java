package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.restaurant.RestaurantDashboardDTO;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.RestaurantDashboardServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/restaurant-dashboard")
@RequiredArgsConstructor
public class RestaurantDashboardController {

    private final RestaurantDashboardServiceImpl dashboardService;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;

    @GetMapping("/{restaurantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public ResponseEntity<RestaurantDashboardDTO> getDashboard(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(dashboardService.getDashboard(restaurantId));
    }

    @GetMapping("/mon-restaurant")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<RestaurantDashboardDTO> getMonDashboard(
            @AuthenticationPrincipal String email) {
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();

        Long restaurantId = restaurantRepository.findByOwnerId(userId)
                .map(r -> r.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun restaurant trouvé"));

        return ResponseEntity.ok(dashboardService.getDashboard(restaurantId));
    }
}
