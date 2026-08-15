package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.dtos.AppleAuthRequestDTO;
import ma.mysuguclientapp.dtos.AppleIdTokenClaimsDTO;
import ma.mysuguclientapp.dtos.GoogleAuthRequestDTO;
import ma.mysuguclientapp.dtos.GoogleTokenInfoDTO;
import ma.mysuguclientapp.dtos.LocalisationDTO;
import ma.mysuguclientapp.dtos.LocationUpdateDTO;
import ma.mysuguclientapp.dtos.LoginDTO;
import ma.mysuguclientapp.dtos.LoginResponseDTO;
import ma.mysuguclientapp.dtos.RegisterDTO;
import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.dtos.UserUpdateDTO;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.exceptions.UnauthorizedException;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.UserService;
import ma.mysuguclientapp.services.interfaces.AuthEnhancedService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
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
    private final GoogleAuthService googleAuthService;
    private final AppleAuthService appleAuthService;
    private final AuthEnhancedService authEnhancedService;
    private final ma.mysuguclientapp.repositories.TokenVerificationRepository tokenVerificationRepository;
    private final EmailService emailService;

    @Override
    public UserDTO register(RegisterDTO registerDTO) {
        if (userRepository.findByEmail(registerDTO.getEmail()).isPresent()) {
            throw new BadRequestException("Un utilisateur avec cet email existe déjà");
        }

        User user = new User();
        user.setEmail(registerDTO.getEmail());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNom(registerDTO.getNom());
        user.setPrenom(registerDTO.getPrenom());
        user.setTelephone(registerDTO.getTelephone());
        user.setRole(parseRole(registerDTO.getRole(), false));
        user.setIsActive(true);

        User savedUser = userRepository.save(user);
        authEnhancedService.envoyerEmailVerification(savedUser.getId());
        log.info("Utilisateur créé avec succès: {}", savedUser.getEmail());
        return convertToDTO(savedUser);
    }

    @Override
    public LoginResponseDTO login(LoginDTO loginDTO) {
        User user = userRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Email ou mot de passe incorrect"));

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Email ou mot de passe incorrect");
        }
        if (!user.getIsActive()) {
            throw new UnauthorizedException("Compte désactivé");
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Email non vérifié. Consultez votre boîte e-mail pour vérifier votre compte.");
        }

        return buildLoginResponse(user);
    }

    @Override
    public LoginResponseDTO loginWithGoogle(GoogleAuthRequestDTO googleAuthRequestDTO) {
        GoogleTokenInfoDTO tokenInfo = googleAuthService.verifyIdToken(googleAuthRequestDTO.getIdToken());

        User user = userRepository.findByEmail(tokenInfo.getEmail())
                .map(existingUser -> updateUserFromGoogle(existingUser, tokenInfo, googleAuthRequestDTO))
                .orElseGet(() -> createGoogleUser(tokenInfo, googleAuthRequestDTO));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Compte désactivé");
        }

        user = userRepository.save(user);
        log.info("Connexion Google réussie pour: {}", user.getEmail());
        return buildLoginResponse(user);
    }

    @Override
    public LoginResponseDTO loginWithApple(AppleAuthRequestDTO appleAuthRequestDTO) {
        AppleIdTokenClaimsDTO tokenClaims = appleAuthService.verifyIdToken(
                appleAuthRequestDTO.getIdToken(),
                appleAuthRequestDTO.getNonce()
        );

        User user = userRepository.findByAppleSub(tokenClaims.getSub())
                .map(existingUser -> updateUserFromApple(existingUser, tokenClaims, appleAuthRequestDTO))
                .orElseGet(() -> createAppleUser(tokenClaims, appleAuthRequestDTO));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Compte désactivé");
        }

        user = userRepository.save(user);
        log.info("Connexion Apple réussie pour: {}", user.getEmail());
        return buildLoginResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getProfile(String token) {
        String jwt = extractToken(token);
        String email = jwtTokenProvider.getEmailFromToken(jwt);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_MESSAGE));

        return convertToDTO(user);
    }

    @Override
    public UserDTO updateProfile(String token, UserUpdateDTO updateDTO, MultipartFile avatar) {
        String jwt = extractToken(token);
        String email = jwtTokenProvider.getEmailFromToken(jwt);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_MESSAGE));

        boolean emailChanged = false;
        if (updateDTO.getEmail() != null && !updateDTO.getEmail().isBlank()) {
            String newEmail = updateDTO.getEmail().trim().toLowerCase();
            if (!newEmail.equalsIgnoreCase(user.getEmail())) {
                userRepository.findByEmail(newEmail).ifPresent(existing -> {
                    if (!existing.getId().equals(user.getId())) {
                        throw new BadRequestException("Un utilisateur avec cet email existe déjà");
                    }
                });
                user.setEmail(newEmail);
                user.setEmailVerified(false);
                emailChanged = true;
            }
        }
        if (updateDTO.getNom() != null) {
            user.setNom(updateDTO.getNom());
        }
        if (updateDTO.getPrenom() != null) {
            user.setPrenom(updateDTO.getPrenom());
        }
        if (updateDTO.getTelephone() != null) {
            user.setTelephone(updateDTO.getTelephone());
        }
        if (updateDTO.getLocalisation() != null) {
            user.setLocalisation(Localisation.builder()
                    .latitude(updateDTO.getLocalisation().getLatitude())
                    .longitude(updateDTO.getLocalisation().getLongitude())
                    .adresse(updateDTO.getLocalisation().getAdresse())
                    .ville(updateDTO.getLocalisation().getVille())
                    .codePostal(updateDTO.getLocalisation().getCodePostal())
                    .pays(updateDTO.getLocalisation().getPays())
                    .build());
        }

        if (avatar != null && !avatar.isEmpty()) {
            try {
                if (user.getAvatar() != null) {
                    minioService.deleteFile(user.getAvatar());
                }
                user.setAvatar(minioService.uploadFile(avatar, "avatars"));
            } catch (Exception e) {
                log.error("Erreur lors de l'upload de l'avatar", e);
                throw new BadRequestException("Erreur lors de l'upload de l'avatar");
            }
        }

        User savedUser = userRepository.save(user);
        if (emailChanged) {
            authEnhancedService.envoyerEmailVerification(savedUser.getId());
        }
        return convertToDTO(savedUser);
    }

    @Override
    public UserDTO updateLocation(String token, LocationUpdateDTO locationDTO) {
        String jwt = extractToken(token);
        String email = jwtTokenProvider.getEmailFromToken(jwt);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_MESSAGE));

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
        user.setLastLocationAt(LocalDateTime.now());
        return convertToDTO(userRepository.save(user));
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
        // Filtre sur isActive=true ET livreurDisponible=true (correction du bug précédent)
        return userRepository.findByRoleAndIsActiveAndLivreurDisponible(UserRole.LIVREUR, true, true).stream()
                .filter(livreur -> {
                    if (livreur.getLocalisation() == null
                            || livreur.getLocalisation().getLatitude() == null
                            || livreur.getLocalisation().getLongitude() == null) {
                        return false;
                    }
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
    public UserDTO setDisponibilite(String token, Boolean disponible) {
        String jwt = extractToken(token);
        String email = jwtTokenProvider.getEmailFromToken(jwt);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_MESSAGE));

        if (user.getRole() != UserRole.LIVREUR) {
            throw new BadRequestException("Seuls les livreurs peuvent modifier leur disponibilité");
        }

        user.setLivreurDisponible(disponible);
        log.info("Disponibilité du livreur {} mise à jour: {}", user.getEmail(), disponible);
        return convertToDTO(userRepository.save(user));
    }

    @Override
    public UserDTO toggleUserStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'ID: " + id));

        user.setIsActive(!user.getIsActive());
        return convertToDTO(userRepository.save(user));
    }

    private User createGoogleUser(GoogleTokenInfoDTO tokenInfo, GoogleAuthRequestDTO googleAuthRequestDTO) {
        User user = new User();
        user.setEmail(tokenInfo.getEmail());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setNom(resolveLastName(tokenInfo));
        user.setPrenom(resolveFirstName(tokenInfo));
        user.setTelephone(googleAuthRequestDTO.getTelephone());
        user.setAvatar(tokenInfo.getPicture());
        user.setRole(parseRole(googleAuthRequestDTO.getRole(), true));
        user.setIsActive(true);
        return user;
    }

    private User createAppleUser(AppleIdTokenClaimsDTO tokenClaims, AppleAuthRequestDTO appleAuthRequestDTO) {
        String email = resolveAppleEmail(tokenClaims, appleAuthRequestDTO);
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BadRequestException("Un compte existe déjà avec cet email. Connectez-vous avec votre méthode habituelle.");
        }

        User user = new User();
        user.setAppleSub(tokenClaims.getSub());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setPrenom(resolveAppleFirstName(appleAuthRequestDTO));
        user.setNom(resolveAppleLastName(appleAuthRequestDTO, email));
        user.setTelephone(appleAuthRequestDTO.getTelephone());
        user.setRole(parseRole(appleAuthRequestDTO.getRole(), true));
        user.setIsActive(true);
        user.setEmailVerified(tokenClaims.isEmailVerified());
        return user;
    }

    private User updateUserFromApple(User user, AppleIdTokenClaimsDTO tokenClaims, AppleAuthRequestDTO appleAuthRequestDTO) {
        if (user.getAppleSub() == null || user.getAppleSub().isBlank()) {
            user.setAppleSub(tokenClaims.getSub());
        }
        if ((user.getPrenom() == null || user.getPrenom().isBlank())
                && appleAuthRequestDTO.getPrenom() != null && !appleAuthRequestDTO.getPrenom().isBlank()) {
            user.setPrenom(appleAuthRequestDTO.getPrenom().trim());
        }
        if ((user.getNom() == null || user.getNom().isBlank())
                && appleAuthRequestDTO.getNom() != null && !appleAuthRequestDTO.getNom().isBlank()) {
            user.setNom(appleAuthRequestDTO.getNom().trim());
        }
        if ((user.getTelephone() == null || user.getTelephone().isBlank())
                && appleAuthRequestDTO.getTelephone() != null && !appleAuthRequestDTO.getTelephone().isBlank()) {
            user.setTelephone(appleAuthRequestDTO.getTelephone());
        }
        if (tokenClaims.isEmailVerified()) {
            user.setEmailVerified(true);
        }
        return user;
    }

    private String resolveAppleEmail(AppleIdTokenClaimsDTO tokenClaims, AppleAuthRequestDTO appleAuthRequestDTO) {
        if (tokenClaims.getEmail() != null && !tokenClaims.getEmail().isBlank()) {
            return tokenClaims.getEmail().trim().toLowerCase();
        }
        if (appleAuthRequestDTO.getEmail() != null && !appleAuthRequestDTO.getEmail().isBlank()) {
            return appleAuthRequestDTO.getEmail().trim().toLowerCase();
        }
        return "apple." + tokenClaims.getSub() + "@mysuku.apple.local";
    }

    private String resolveAppleFirstName(AppleAuthRequestDTO appleAuthRequestDTO) {
        if (appleAuthRequestDTO.getPrenom() != null && !appleAuthRequestDTO.getPrenom().isBlank()) {
            return appleAuthRequestDTO.getPrenom().trim();
        }
        return "Utilisateur";
    }

    private String resolveAppleLastName(AppleAuthRequestDTO appleAuthRequestDTO, String email) {
        if (appleAuthRequestDTO.getNom() != null && !appleAuthRequestDTO.getNom().isBlank()) {
            return appleAuthRequestDTO.getNom().trim();
        }
        return email;
    }

    private User updateUserFromGoogle(User user, GoogleTokenInfoDTO tokenInfo, GoogleAuthRequestDTO googleAuthRequestDTO) {
        if ((user.getPrenom() == null || user.getPrenom().isBlank()) && resolveFirstName(tokenInfo) != null) {
            user.setPrenom(resolveFirstName(tokenInfo));
        }
        if ((user.getNom() == null || user.getNom().isBlank()) && resolveLastName(tokenInfo) != null) {
            user.setNom(resolveLastName(tokenInfo));
        }
        if ((user.getAvatar() == null || user.getAvatar().isBlank()) && tokenInfo.getPicture() != null) {
            user.setAvatar(tokenInfo.getPicture());
        }
        if ((user.getTelephone() == null || user.getTelephone().isBlank())
                && googleAuthRequestDTO.getTelephone() != null && !googleAuthRequestDTO.getTelephone().isBlank()) {
            user.setTelephone(googleAuthRequestDTO.getTelephone());
        }
        return user;
    }

    private LoginResponseDTO buildLoginResponse(User user) {
        String token = jwtTokenProvider.generateToken(user);
        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        response.setUser(convertToDTO(user));
        return response;
    }

    private UserRole parseRole(String role, boolean defaultClient) {
        if (role == null || role.isBlank()) {
            if (defaultClient) {
                return UserRole.CLIENT;
            }
            throw new BadRequestException("Rôle invalide: " + role);
        }

        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Rôle invalide: " + role);
        }
    }

    private String resolveFirstName(GoogleTokenInfoDTO tokenInfo) {
        if (tokenInfo.getGivenName() != null && !tokenInfo.getGivenName().isBlank()) {
            return tokenInfo.getGivenName();
        }
        if (tokenInfo.getName() != null && tokenInfo.getName().contains(" ")) {
            return tokenInfo.getName().split(" ")[0];
        }
        return tokenInfo.getName();
    }

    private String resolveLastName(GoogleTokenInfoDTO tokenInfo) {
        if (tokenInfo.getFamilyName() != null && !tokenInfo.getFamilyName().isBlank()) {
            return tokenInfo.getFamilyName();
        }
        if (tokenInfo.getName() != null && tokenInfo.getName().contains(" ")) {
            String[] parts = tokenInfo.getName().split(" ", 2);
            return parts.length > 1 ? parts[1] : parts[0];
        }
        return tokenInfo.getEmail();
    }

    private String extractToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new BadRequestException("Token invalide");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserDTO> getUsersByRole(String role, String search, Pageable pageable) {
        UserRole userRole = parseRole(role, false);
        Page<User> page;
        if (search != null && !search.isBlank()) {
            page = userRepository.findByRoleAndSearch(userRole, search, pageable);
        } else {
            page = userRepository.findByRole(userRole, pageable);
        }
        return page.map(this::convertToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserDTO> getProprietairesParVerticale(ma.mysuguclientapp.enumerations.Vertical vertical,
                                                      String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return userRepository.findProprietairesByVerticalAndSearch(vertical, search.trim(), pageable)
                    .map(this::convertToDTO);
        }
        return userRepository.findProprietairesByVertical(vertical, pageable)
                .map(this::convertToDTO);
    }

    @Override
    @Transactional
    public UserDTO mettreAJourUtilisateur(Long id, ma.mysuguclientapp.dtos.auth.AdminUserUpdateDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'ID: " + id));

        // Champ absent = champ inchangé. Ni role, ni mot de passe, ni email : l'email est
        // l'identifiant de connexion, le changer couperait l'acces du proprietaire.
        if (dto.getNom() != null) user.setNom(dto.getNom());
        if (dto.getPrenom() != null) user.setPrenom(dto.getPrenom());
        if (dto.getTelephone() != null) user.setTelephone(dto.getTelephone());

        return convertToDTO(userRepository.save(user));
    }

    @Override
    @Transactional
    public void relancerInvitation(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'ID: " + id));

        if (user.getRole() != UserRole.RESTAURANT_OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "L'invitation ne concerne que les propriétaires d'établissement");
        }

        // Meme mecanique que OwnerProvisioningServiceImpl : un jeton PASSWORD_RESET de 24h
        // et le mail d'invitation, qui pointe sur /definir-mot-de-passe?token=.
        String token = java.util.UUID.randomUUID().toString();
        tokenVerificationRepository.save(
                ma.mysuguclientapp.entities.TokenVerification.builder()
                        .user(user)
                        .token(token)
                        .type("PASSWORD_RESET")
                        .expiresAt(java.time.LocalDateTime.now().plusHours(24))
                        .build());
        emailService.envoyerInvitationRestaurateur(user.getEmail(),
                user.getPrenom() + " " + user.getNom(), token);
        log.info("Invitation relancée pour {}", user.getEmail());
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
        dto.setLivreurDisponible(user.getLivreurDisponible());
        dto.setCreatedAt(user.getCreatedAt());

        if (user.getLocalisation() != null) {
            dto.setLocalisation(LocalisationDTO.builder()
                    .latitude(user.getLocalisation().getLatitude())
                    .longitude(user.getLocalisation().getLongitude())
                    .adresse(user.getLocalisation().getAdresse())
                    .ville(user.getLocalisation().getVille())
                    .codePostal(user.getLocalisation().getCodePostal())
                    .pays(user.getLocalisation().getPays())
                    .build());
        }

        return dto;
    }

    private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return Double.MAX_VALUE;
        }

        final int r = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}
