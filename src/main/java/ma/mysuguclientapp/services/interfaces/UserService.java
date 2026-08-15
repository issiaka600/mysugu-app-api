package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.AppleAuthRequestDTO;
import ma.mysuguclientapp.dtos.GoogleAuthRequestDTO;
import ma.mysuguclientapp.dtos.LoginDTO;
import ma.mysuguclientapp.dtos.LoginResponseDTO;
import ma.mysuguclientapp.dtos.LocationUpdateDTO;
import ma.mysuguclientapp.dtos.RegisterDTO;
import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.dtos.UserUpdateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    UserDTO register(RegisterDTO registerDTO);

    LoginResponseDTO login(LoginDTO loginDTO);

    LoginResponseDTO loginWithGoogle(GoogleAuthRequestDTO googleAuthRequestDTO);

    LoginResponseDTO loginWithApple(AppleAuthRequestDTO appleAuthRequestDTO);

    UserDTO getProfile(String token);

    UserDTO updateProfile(String token, UserUpdateDTO updateDTO, MultipartFile avatar);

    UserDTO updateLocation(String token, LocationUpdateDTO locationDTO);

    UserDTO getUserById(Long id);

    List<UserDTO> getAvailableLivreurs(Double latitude, Double longitude, Double radiusKm);

    UserDTO toggleUserStatus(Long id);

    /**
     * Permet à un livreur de mettre à jour sa disponibilité (disponible/indisponible).
     * Met aussi à jour le flag en BDD de façon cohérente.
     */
    UserDTO setDisponibilite(String token, Boolean disponible);

    Page<UserDTO> getUsersByRole(String role, String search, Pageable pageable);

    /**
     * Propriétaires d'établissement d'une verticale. {@code NULL} en base vaut {@code RESTAURANT}.
     */
    Page<UserDTO> getProprietairesParVerticale(ma.mysuguclientapp.enumerations.Vertical vertical,
                                               String search, Pageable pageable);

    /**
     * Mise à jour administrative des coordonnées. Ne touche ni au rôle, ni au mot de passe,
     * ni à l'email. Un champ absent du DTO reste inchangé.
     */
    UserDTO mettreAJourUtilisateur(Long id, ma.mysuguclientapp.dtos.auth.AdminUserUpdateDTO dto);

    /**
     * Renvoie au propriétaire le lien de définition de mot de passe.
     * Réutilise le parcours d'invitation restaurateur : aucun mot de passe ne transite.
     */
    void relancerInvitation(Long id);
}
