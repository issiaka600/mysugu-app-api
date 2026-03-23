package ma.mysuguclientapp.services.interfaces;

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

    UserDTO getProfile(String token);

    UserDTO updateProfile(String token, UserUpdateDTO updateDTO, MultipartFile avatar);

    UserDTO updateLocation(String token, LocationUpdateDTO locationDTO);

    UserDTO getUserById(Long id);

    List<UserDTO> getAvailableLivreurs(Double latitude, Double longitude, Double radiusKm);

    UserDTO toggleUserStatus(Long id);

    Page<UserDTO> getUsersByRole(String role, String search, Pageable pageable);
}
