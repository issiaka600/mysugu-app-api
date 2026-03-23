package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.GoogleAuthRequestDTO;
import ma.mysuguclientapp.dtos.LocationUpdateDTO;
import ma.mysuguclientapp.dtos.LoginDTO;
import ma.mysuguclientapp.dtos.LoginResponseDTO;
import ma.mysuguclientapp.dtos.RegisterDTO;
import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.dtos.UserUpdateDTO;
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

    @PostMapping("auth/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(registerDTO));
    }

    @PostMapping("auth/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return ResponseEntity.ok(userService.login(loginDTO));
    }

    @PostMapping("auth/google")
    public ResponseEntity<LoginResponseDTO> loginWithGoogle(@RequestBody GoogleAuthRequestDTO googleAuthRequestDTO) {
        return ResponseEntity.ok(userService.loginWithGoogle(googleAuthRequestDTO));
    }

    @GetMapping("users/profile")
    public ResponseEntity<UserDTO> getProfile(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(userService.getProfile(token));
    }

    @PutMapping(value = "users/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserDTO> updateProfile(
            @RequestHeader("Authorization") String token,
            @ModelAttribute UserUpdateDTO updateDTO,
            @RequestParam(required = false) MultipartFile avatar) {
        return ResponseEntity.ok(userService.updateProfile(token, updateDTO, avatar));
    }

    @PatchMapping("users/location")
    public ResponseEntity<UserDTO> updateLocation(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody LocationUpdateDTO locationDTO) {
        return ResponseEntity.ok(userService.updateLocation(token, locationDTO));
    }

    @GetMapping("livreurs/disponibles")
    public ResponseEntity<?> getAvailableLivreurs(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "10.0") Double radiusKm) {
        return ResponseEntity.ok(userService.getAvailableLivreurs(latitude, longitude, radiusKm));
    }
}
