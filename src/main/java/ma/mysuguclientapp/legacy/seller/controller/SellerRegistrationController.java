package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.LoginDTO;
import ma.mysuguclientapp.dtos.LoginResponseDTO;
import ma.mysuguclientapp.dtos.RegisterDTO;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.legacy.seller.dto.ErrorsResponse;
import ma.mysuguclientapp.legacy.seller.dto.TokenResponse;
import ma.mysuguclientapp.services.interfaces.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Inscription du shim vendeur (contrat 6valley POST /api/v3/seller/registration, multipart).
 * Réutilise le parcours natif d'inscription (comme {@code RestaurateurController.register}) en
 * forçant le rôle RESTAURANT_OWNER, puis renvoie le token 6valley {token} (auto-login).
 *
 * <p>La création de la boutique (shop_name / shop_address / bannières) est traitée séparément
 * dans une tranche ultérieure (3b) : ici seul le compte vendeur est créé, conformément au
 * classement PARTIAL du design (docs/superpowers/specs/2026-07-10-vendor-3a-auth-design.md §2).</p>
 */
@RestController
@RequestMapping("/api/v3/seller")
@RequiredArgsConstructor
@Slf4j
public class SellerRegistrationController {

    private final UserService userService;

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

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setEmail(email);
        loginDTO.setPassword(password);
        LoginResponseDTO login = userService.login(loginDTO);
        log.info("Inscription vendeur réussie pour {}", email);
        return ResponseEntity.ok(new TokenResponse(login.getToken()));
    }
}
