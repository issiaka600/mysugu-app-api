package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Revue des demandes d'onboarding restaurateur par l'admin.
 *
 * <p>Sous {@code /api/admin/**} : sécurisé ADMIN par la configuration globale.
 * L'admin peut lister les restaurants en attente puis approuver, rejeter
 * (avec motif) ou demander des justificatifs complémentaires.</p>
 */
@RestController
@RequestMapping("/api/admin/restaurants")
@RequiredArgsConstructor
public class AdminRestaurantController {

    private final RestaurantService restaurantService;
    private final UserRepository userRepository;

    /** Restaurants en attente de revue (EN_ATTENTE ou COMPLEMENT_REQUIS). */
    @GetMapping("/a-reviser")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RestaurantDTO>> getARreviser() {
        return ResponseEntity.ok(restaurantService.getRestaurantsAReviser());
    }

    @PatchMapping("/{id}/approuver")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestaurantDTO> approuver(@PathVariable Long id,
                                                   @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(restaurantService.approuverRestaurant(id, resolveAdminId(email)));
    }

    @PatchMapping("/{id}/rejeter")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestaurantDTO> rejeter(@PathVariable Long id,
                                                 @RequestParam String motif,
                                                 @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(restaurantService.rejeterRestaurant(id, resolveAdminId(email), motif));
    }

    @PatchMapping("/{id}/demander-complement")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestaurantDTO> demanderComplement(@PathVariable Long id,
                                                            @RequestParam String message,
                                                            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(restaurantService.demanderComplement(id, resolveAdminId(email), message));
    }

    private Long resolveAdminId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }
}
