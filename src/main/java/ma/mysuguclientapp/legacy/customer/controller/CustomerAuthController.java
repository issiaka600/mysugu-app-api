package ma.mysuguclientapp.legacy.customer.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.LoginDTO;
import ma.mysuguclientapp.dtos.LoginResponseDTO;
import ma.mysuguclientapp.dtos.RegisterDTO;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.UnauthorizedException;
import ma.mysuguclientapp.services.interfaces.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Auth shim client (contrat 6valley {@code /api/v1/auth/*}) pour l'app MySuKu : elle ne change
 * que sa base URL et attend {@code POST /api/v1/auth/login} / {@code /register} renvoyant un
 * simple {@code {token}} (jamais {@code temporary_token}), avec des erreurs au format
 * {@code {errors:[{code,message}]}} — voir {@code docs/legacy-contracts/CONTRACT-REFERENCE.md}.
 *
 * <p>Délègue à {@link UserService#register} / {@link UserService#login} (chemin natif déjà
 * utilisé par {@code UserController} et {@code RestaurateurController}) pour garder le hashing du
 * mot de passe, l'assignation du rôle et les futurs effets de bord identiques à un compte créé
 * directement via l'app MySugu — un client inscrit via MySuKu doit être indiscernable d'un client
 * inscrit nativement.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class CustomerAuthController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        String email = str(body.get("email"));
        String password = str(body.get("password"));
        if (email == null || password == null) {
            return errors(HttpStatus.FORBIDDEN, "credential", "Email et mot de passe requis.");
        }
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setEmail(email);
        loginDTO.setPassword(password);
        try {
            LoginResponseDTO resp = userService.login(loginDTO);
            return ResponseEntity.ok(Map.of("token", resp.getToken())); // jamais temporary_token
        } catch (UnauthorizedException e) {
            return errors(HttpStatus.UNAUTHORIZED, "auth-001", "Identifiants incorrects.");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {
        String email = str(body.get("email"));
        String phone = str(body.get("phone"));
        String password = str(body.get("password"));
        if (email == null || phone == null || password == null || password.length() < 8) {
            return errors(HttpStatus.FORBIDDEN, "validation", "Champs requis (mot de passe >= 8 caractères).");
        }
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setEmail(email);
        registerDTO.setPassword(password);
        registerDTO.setNom(str(body.get("l_name")));
        registerDTO.setPrenom(str(body.get("f_name")));
        registerDTO.setTelephone(phone);
        registerDTO.setRole("CLIENT");
        try {
            userService.register(registerDTO);
        } catch (BadRequestException e) {
            return errors(HttpStatus.FORBIDDEN, "email", e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Compte créé. Vérifiez votre e-mail avant de vous connecter."));
    }

    private static String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private static ResponseEntity<?> errors(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("errors", List.of(Map.of("code", code, "message", message))));
    }
}
