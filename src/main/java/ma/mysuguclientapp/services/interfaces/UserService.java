package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    /**
     * Inscription d'un nouvel utilisateur
     */
    UserDTO register(RegisterDTO registerDTO);

    /**
     * Connexion d'un utilisateur
     */
    LoginResponseDTO login(LoginDTO loginDTO);

    /**
     * Obtenir le profil de l'utilisateur connecté
     */
    UserDTO getProfile(String token);

    /**
     * Mettre à jour le profil utilisateur
     */
    UserDTO updateProfile(String token, UserUpdateDTO updateDTO, MultipartFile avatar);

    /**
     * Mettre à jour la localisation
     */
    UserDTO updateLocation(String token, LocationUpdateDTO locationDTO);

    /**
     * Obtenir un utilisateur par ID
     */
    UserDTO getUserById(Long id);

    /**
     * Obtenir les livreurs disponibles dans une zone
     */
    List<UserDTO> getAvailableLivreurs(Double latitude, Double longitude, Double radiusKm);

    /**
     * Activer/désactiver un utilisateur
     */
    UserDTO toggleUserStatus(Long id);
}
