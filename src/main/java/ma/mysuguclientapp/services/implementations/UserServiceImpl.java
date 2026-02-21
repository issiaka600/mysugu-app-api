package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.dtos.*;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.exceptions.UnauthorizedException;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {
    private static final String USER_NOT_FOUND_MESSAGE = "Utilisateur non trouvé";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final MinioService minioService;

    @Override
    public UserDTO register(RegisterDTO registerDTO) {
        // Vérifier si l'email existe déjà
        if (userRepository.findByEmail(registerDTO.getEmail()).isPresent()) {
            throw new BadRequestException("Un utilisateur avec cet email existe déjà");
        }

        // Créer l'utilisateur
        User user = new User();
        user.setEmail(registerDTO.getEmail());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNom(registerDTO.getNom());
        user.setPrenom(registerDTO.getPrenom());
        user.setTelephone(registerDTO.getTelephone());

        try {
            user.setRole(UserRole.valueOf(registerDTO.getRole()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Rôle invalide: " + registerDTO.getRole());
        }

        user.setIsActive(true);

        User savedUser = userRepository.save(user);
        log.info("Utilisateur créé avec succès: {}", savedUser.getEmail());

        return convertToDTO(savedUser);
    }

    @Override
    public LoginResponseDTO login(LoginDTO loginDTO) {
        // Trouver l'utilisateur par email
        User user = userRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Email ou mot de passe incorrect"));

        // Vérifier le mot de passe
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Email ou mot de passe incorrect");
        }

        // Vérifier si l'utilisateur est actif
        if (!user.getIsActive()) {
            throw new UnauthorizedException("Compte désactivé");
        }

        // Générer le token JWT
        String token = jwtTokenProvider.generateToken(user);
        log.info("Connexion réussie pour: {}", user.getEmail());

        // Créer la réponse
        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        response.setUser(convertToDTO(user));

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getProfile(String token) {
        // Extraire le token Bearer
        String jwt = extractToken(token);

        // Obtenir l'email depuis le token
        String email = jwtTokenProvider.getEmailFromToken(jwt);

        // Trouver l'utilisateur
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_MESSAGE));

        return convertToDTO(user);
    }

    @Override
    public UserDTO updateProfile(String token, UserUpdateDTO updateDTO, MultipartFile avatar) {
        // Obtenir l'utilisateur connecté
        String jwt = extractToken(token);
        String email = jwtTokenProvider.getEmailFromToken(jwt);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_MESSAGE));

        // Mettre à jour les informations
        if (updateDTO.getNom() != null) {
            user.setNom(updateDTO.getNom());
        }
        if (updateDTO.getPrenom() != null) {
            user.setPrenom(updateDTO.getPrenom());
        }
        if (updateDTO.getTelephone() != null) {
            user.setTelephone(updateDTO.getTelephone());
        }

        // Mettre à jour la localisation
        if (updateDTO.getLocalisation() != null) {

            Localisation localisation = Localisation.builder()
                    .latitude(updateDTO.getLocalisation().getLatitude())
                    .longitude(updateDTO.getLocalisation().getLongitude())
                    .adresse(updateDTO.getLocalisation().getAdresse())
                    .ville(updateDTO.getLocalisation().getVille())
                    .codePostal(updateDTO.getLocalisation().getCodePostal())
                    .pays(updateDTO.getLocalisation().getPays())
                    .build();
            user.setLocalisation(localisation);

        }

        // Upload avatar si fourni
        if (avatar != null && !avatar.isEmpty()) {
            try {
                // Supprimer l'ancien avatar si existe
                if (user.getAvatar() != null) {
                    minioService.deleteFile(user.getAvatar());
                }

                // Upload le nouveau
                String avatarUrl = minioService.uploadFile(avatar, "avatars");
                user.setAvatar(avatarUrl);
            } catch (Exception e) {
                log.error("Erreur lors de l'upload de l'avatar", e);
                throw new BadRequestException("Erreur lors de l'upload de l'avatar");
            }
        }

        User updatedUser = userRepository.save(user);
        log.info("Profil mis à jour pour: {}", user.getEmail());

        return convertToDTO(updatedUser);
    }

    @Override
    public UserDTO updateLocation(String token, LocationUpdateDTO locationDTO) {
        // Obtenir l'utilisateur connecté
        String jwt = extractToken(token);
        String email = jwtTokenProvider.getEmailFromToken(jwt);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_MESSAGE));

        // Mettre à jour ou créer la localisation
        Localisation localisation = user.getLocalisation();
        if (localisation == null) {
            localisation = new Localisation();
        }

        localisation.setLatitude(locationDTO.getLatitude());
        localisation.setLongitude(locationDTO.getLongitude());
        localisation.setAdresse(locationDTO.getAdresse());
        localisation.setVille(locationDTO.getVille());
        localisation.setPays(locationDTO.getPays());
        localisation.setCodePostal(locationDTO.getCodePostal());

        user.setLocalisation(localisation);

        User updatedUser = userRepository.save(user);
        log.info("Localisation mise à jour pour: {}", user.getEmail());

        return convertToDTO(updatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'ID: " + id));
        return convertToDTO(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> getAvailableLivreurs(Double latitude, Double longitude, Double radiusKm) {
        // Récupérer tous les livreurs actifs
        List<User> livreurs = userRepository.findByRoleAndIsActive(UserRole.LIVREUR, true);

        // Filtrer par distance
        return livreurs.stream()
                .filter(livreur -> {
                    if (livreur.getLocalisation() == null) return false;

                    double distance = calculateDistance(
                            latitude, longitude,
                            livreur.getLocalisation().getLatitude(),
                            livreur.getLocalisation().getLongitude()
                    );

                    return distance <= radiusKm;
                })
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO toggleUserStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'ID: " + id));

        user.setIsActive(!user.getIsActive());
        User updatedUser = userRepository.save(user);

        log.info("Statut de l'utilisateur {} changé à: {}", user.getEmail(), user.getIsActive());

        return convertToDTO(updatedUser);
    }

    // ========== MÉTHODES UTILITAIRES ==========

    private String extractToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new BadRequestException("Token invalide");
    }

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setNom(user.getNom());
        dto.setPrenom(user.getPrenom());
        dto.setTelephone(user.getTelephone());
        dto.setRole(user.getRole().name());
        dto.setAvatar(user.getAvatar());
        dto.setIsActive(user.getIsActive());

        if (user.getLocalisation() != null) {
            LocalisationDTO localisationDTO = LocalisationDTO.builder()
                    .latitude(user.getLocalisation().getLatitude())
                    .longitude(user.getLocalisation().getLongitude())
                    .adresse(user.getLocalisation().getAdresse())
                    .ville(user.getLocalisation().getVille())
                    .codePostal(user.getLocalisation().getCodePostal())
                    .pays(user.getLocalisation().getPays())
                    .build();
            dto.setLocalisation(localisationDTO);
        }

        return dto;
    }

    /**
     * Calcul de distance avec formule Haversine (en km)
     */
    private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return Double.MAX_VALUE;
        }

        final int R = 6371; // Rayon de la Terre en km

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}
