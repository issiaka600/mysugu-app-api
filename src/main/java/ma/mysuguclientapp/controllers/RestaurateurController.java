package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.LoginDTO;
import ma.mysuguclientapp.dtos.LoginResponseDTO;
import ma.mysuguclientapp.dtos.RegisterDTO;
import ma.mysuguclientapp.dtos.RestaurantCreateDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
import ma.mysuguclientapp.services.interfaces.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Endpoints destinés à l'application mobile restaurateur.
 *
 * <p>Parcours : inscription du restaurateur ({@code /register}), soumission de son
 * restaurant ({@code POST /restaurant}) qui passe alors en attente d'approbation,
 * consultation du statut ({@code GET /mon-restaurant}) et envoi de justificatifs
 * complémentaires à la demande de l'admin ({@code POST /restaurant/justificatifs}).</p>
 */
@RestController
@RequestMapping("/api/restaurateur")
@RequiredArgsConstructor
@Slf4j
public class RestaurateurController {

    private final UserService userService;
    private final RestaurantService restaurantService;

    /**
     * Inscription d'un compte restaurateur (public). Le rôle RESTAURANT_OWNER est
     * forcé quelle que soit la valeur envoyée. Retourne directement un token de session.
     */
    @PostMapping("/register")
    public ResponseEntity<LoginResponseDTO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        registerDTO.setRole("RESTAURANT_OWNER");
        userService.register(registerDTO);

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setEmail(registerDTO.getEmail());
        loginDTO.setPassword(registerDTO.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.login(loginDTO));
    }

    /**
     * Soumission du restaurant par le restaurateur connecté. Le restaurant est créé
     * en statut EN_ATTENTE et n'est pas visible tant qu'il n'est pas approuvé.
     */
    @PostMapping(value = "/restaurant", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<RestaurantDTO> soumettreRestaurant(
            @AuthenticationPrincipal String email,
            @ModelAttribute RestaurantCreateDTO dto,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "justificatifs", required = false) List<MultipartFile> justificatifs) {
        RestaurantDTO created = restaurantService.soumettreOnboarding(email, dto, logo, justificatifs);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Restaurant du restaurateur connecté, avec son statut d'approbation et le motif
     * éventuel (rejet ou demande de complément).
     */
    @GetMapping("/mon-restaurant")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<RestaurantDTO> getMonRestaurant(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(restaurantService.getMonRestaurant(email));
    }

    /**
     * Ajout de justificatifs complémentaires (après une demande de complément de l'admin).
     */
    @PostMapping(value = "/restaurant/justificatifs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<RestaurantDTO> ajouterJustificatifs(
            @AuthenticationPrincipal String email,
            @RequestParam("justificatifs") List<MultipartFile> justificatifs) {
        return ResponseEntity.ok(restaurantService.ajouterJustificatifs(email, justificatifs));
    }
}
