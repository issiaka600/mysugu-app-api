package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.*;
import ma.mysuguclientapp.services.interfaces.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * POST /api/users/register - Inscription
     */
    @PostMapping("auth/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        UserDTO user = userService.register(registerDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    /**
     * POST /api/users/login - Connexion
     */
    @PostMapping("auth/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        LoginResponseDTO response = userService.login(loginDTO);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/users/profile - Obtenir le profil de l'utilisateur connecté
     */
    @GetMapping("users/profile")
    public ResponseEntity<UserDTO> getProfile(@RequestHeader("Authorization") String token) {
        UserDTO user = userService.getProfile(token);
        return ResponseEntity.ok(user);
    }

    /**
     * PUT /api/users/profile - Mettre à jour le profil
     */
    @PutMapping(value = "users/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserDTO> updateProfile(
            @RequestHeader("Authorization") String token,
            @ModelAttribute UserUpdateDTO updateDTO,
            @RequestParam(required = false) MultipartFile avatar) {
        
        UserDTO updated = userService.updateProfile(token, updateDTO, avatar);
        return ResponseEntity.ok(updated);
    }

    /**
     * PATCH /api/users/location - Mettre à jour la localisation
     */
    @PatchMapping("users/location")
    public ResponseEntity<UserDTO> updateLocation(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody LocationUpdateDTO locationDTO) {
        
        UserDTO updated = userService.updateLocation(token, locationDTO);
        return ResponseEntity.ok(updated);
    }

    /**
     * GET /api/users/livreurs/disponibles - Livreurs disponibles
     */
    @GetMapping("livreurs/disponibles")
    public ResponseEntity<?> getAvailableLivreurs(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "10.0") Double radiusKm) {
        
        return ResponseEntity.ok(userService.getAvailableLivreurs(latitude, longitude, radiusKm));
    }
}
