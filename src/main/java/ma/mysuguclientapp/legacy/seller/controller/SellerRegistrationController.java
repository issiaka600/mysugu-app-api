package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.LocalisationDTO;
import ma.mysuguclientapp.dtos.RegisterDTO;
import ma.mysuguclientapp.dtos.RestaurantCreateDTO;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.legacy.seller.dto.ErrorsResponse;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
import ma.mysuguclientapp.services.interfaces.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Inscription du shim vendeur (contrat 6valley POST /api/v3/seller/registration, multipart).
 * Réutilise le parcours natif d'inscription (comme {@code RestaurateurController.register}) en
 * forçant le rôle RESTAURANT_OWNER. Un e-mail de vérification est envoyé et aucune session
 * n'est délivrée avant sa validation.
 *
 * <p>Tranche 3b : après création du compte RESTAURANT_OWNER, la boutique est créée à partir des
 * champs {@code shop_name}/{@code shop_address} via le parcours natif
 * {@code RestaurantService.soumettreOnboarding} (statut EN_ATTENTE, isActive=false).</p>
 */
@RestController
@RequestMapping("/api/v3/seller")
@RequiredArgsConstructor
@Slf4j
public class SellerRegistrationController {

    private final UserService userService;
    private final RestaurantService restaurantService;

    @PostMapping(value = "/registration", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> registration(
            @RequestParam(value = "f_name", required = false) String fName,
            @RequestParam(value = "l_name", required = false) String lName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam(value = "confirm_password", required = false) String confirmPassword,
            @RequestParam(value = "shop_name", required = false) String shopName,
            @RequestParam(value = "shop_address", required = false) String shopAddress) {

        if (password == null || password.length() < 8
                || (confirmPassword != null && !password.equals(confirmPassword))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorsResponse.of("password", "Mot de passe invalide ou non confirmé (min 8)."));
        }

        RegisterDTO dto = new RegisterDTO();
        dto.setEmail(email);
        dto.setPassword(password);
        dto.setPrenom(fName != null && !fName.isBlank() ? fName : "Vendeur");
        dto.setNom(lName != null && !lName.isBlank() ? lName : "MySugu");
        dto.setTelephone(phone);
        dto.setRole("RESTAURANT_OWNER"); // forcé, quelle que soit l'entrée

        try {
            userService.register(dto);
        } catch (BadRequestException e) {
            // Ex. email déjà utilisé -> forme d'erreur 6valley attendue par l'app.
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorsResponse.of("registration", e.getMessage()));
        }

        // Crée la boutique du vendeur à partir des champs d'inscription (statut EN_ATTENTE) pour
        // que currentRestaurant résolve dès l'inscription. Échec non bloquant : l'inscription
        // (et le token auto-login) reste valide même si l'onboarding boutique échoue.
        try {
            RestaurantCreateDTO shop = new RestaurantCreateDTO();
            shop.setNom(shopName != null && !shopName.isBlank()
                    ? shopName : dto.getPrenom() + " " + dto.getNom());
            if (shopAddress != null && !shopAddress.isBlank()) {
                LocalisationDTO loc = new LocalisationDTO();
                loc.setAdresse(shopAddress);
                shop.setLocalisation(loc);
            }
            restaurantService.soumettreOnboarding(email, shop, null, null);
            log.info("Boutique vendeur créée (EN_ATTENTE) à l'inscription pour {}", email);
        } catch (Exception e) {
            log.warn("Onboarding boutique à l'inscription échoué pour {}: {}", email, e.getMessage());
        }

        log.info("Inscription vendeur réussie pour {}", email);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Compte créé. Vérifiez votre e-mail avant de vous connecter."));
    }
}
