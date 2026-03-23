package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.dtos.auth.*;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.repositories.*;
import ma.mysuguclientapp.services.interfaces.AuthEnhancedService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthEnhancedServiceImpl implements AuthEnhancedService {

    private final UserRepository userRepository;
    private final TokenVerificationRepository tokenVerificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final AdresseLivraisonRepository adresseLivraisonRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override
    @Transactional
    public void envoyerEmailVerification(Long userId) {
        User user = findUser(userId);
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email déjà vérifié");
        }
        String token = UUID.randomUUID().toString();
        TokenVerification tv = TokenVerification.builder()
                .user(user)
                .token(token)
                .type("EMAIL_VERIFICATION")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        tokenVerificationRepository.save(tv);
        emailService.envoyerVerificationEmail(user.getEmail(), token);
    }

    @Override
    @Transactional
    public void verifierEmail(VerifyEmailDTO dto) {
        TokenVerification tv = tokenVerificationRepository.findByTokenAndType(dto.getToken(), "EMAIL_VERIFICATION")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token invalide"));
        if (tv.isExpired()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token expiré");
        if (tv.isUsed()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token déjà utilisé");

        tv.setUsedAt(LocalDateTime.now());
        tokenVerificationRepository.save(tv);

        User user = tv.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void demanderReinitialisationMotDePasse(ForgotPasswordDTO dto) {
        userRepository.findByEmail(dto.getEmail()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            TokenVerification tv = TokenVerification.builder()
                    .user(user)
                    .token(token)
                    .type("PASSWORD_RESET")
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();
            tokenVerificationRepository.save(tv);
            emailService.envoyerReinitialisationMotDePasse(user.getEmail(), token);
        });
        // Always return success to prevent email enumeration
    }

    @Override
    @Transactional
    public void reinitialiserMotDePasse(ResetPasswordDTO dto) {
        TokenVerification tv = tokenVerificationRepository.findByTokenAndType(dto.getToken(), "PASSWORD_RESET")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token invalide"));
        if (tv.isExpired()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token expiré");
        if (tv.isUsed()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token déjà utilisé");

        tv.setUsedAt(LocalDateTime.now());
        tokenVerificationRepository.save(tv);

        User user = tv.getUser();
        user.setPassword(passwordEncoder.encode(dto.getNouveauMotDePasse()));
        userRepository.save(user);

        // Revoke all refresh tokens
        refreshTokenRepository.revokeAllUserTokens(user.getId(), LocalDateTime.now());
    }

    @Override
    @Transactional
    public RefreshTokenResponseDTO rafraichirToken(RefreshTokenRequestDTO dto) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(dto.getRefreshToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide"));

        if (!refreshToken.isValid()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expiré ou révoqué");
        }

        User user = refreshToken.getUser();
        String newAccessToken = jwtTokenProvider.generateToken(user);

        // Rotate refresh token
        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        String newRefreshToken = creerRefreshToken(user);

        return RefreshTokenResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .build();
    }

    @Override
    @Transactional
    public void logout(String accessToken, LogoutDTO dto) {
        // Blacklist the access token
        if (accessToken != null) {
            try {
                Date expiration = jwtTokenProvider.getExpirationFromToken(accessToken);
                LocalDateTime expiresAt = expiration.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                String hash = sha256(accessToken);
                if (!tokenBlacklistRepository.existsByTokenHash(hash)) {
                    TokenBlacklist bl = TokenBlacklist.builder()
                            .tokenHash(hash)
                            .expiresAt(expiresAt)
                            .build();
                    tokenBlacklistRepository.save(bl);
                }
            } catch (Exception e) {
                log.warn("Impossible de blacklister le token: {}", e.getMessage());
            }
        }

        // Revoke refresh token
        if (dto.getRefreshToken() != null) {
            refreshTokenRepository.findByToken(dto.getRefreshToken()).ifPresent(rt -> {
                rt.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(rt);
            });
        }
    }

    @Override
    @Transactional
    public void changerMotDePasse(Long userId, ChangePasswordDTO dto) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(dto.getAncienMotDePasse(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe actuel incorrect");
        }
        user.setPassword(passwordEncoder.encode(dto.getNouveauMotDePasse()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllUserTokens(userId, LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdresseLivraisonDTO> getMesAdresses(Long userId) {
        return adresseLivraisonRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream().map(this::toAdresseDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AdresseLivraisonDTO ajouterAdresse(Long userId, AdresseLivraisonCreateDTO dto) {
        User user = findUser(userId);
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            adresseLivraisonRepository.clearDefaultForUser(userId);
        }
        AdresseLivraison adresse = AdresseLivraison.builder()
                .user(user)
                .libelle(dto.getLibelle())
                .adresse(dto.getAdresse())
                .complement(dto.getComplement())
                .ville(dto.getVille())
                .codePostal(dto.getCodePostal())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .isDefault(dto.getIsDefault() != null && dto.getIsDefault())
                .build();
        return toAdresseDTO(adresseLivraisonRepository.save(adresse));
    }

    @Override
    @Transactional
    public AdresseLivraisonDTO modifierAdresse(Long userId, Long adresseId, AdresseLivraisonCreateDTO dto) {
        AdresseLivraison adresse = adresseLivraisonRepository.findByIdAndUserId(adresseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adresse introuvable"));
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            adresseLivraisonRepository.clearDefaultForUser(userId);
        }
        adresse.setLibelle(dto.getLibelle());
        adresse.setAdresse(dto.getAdresse());
        adresse.setComplement(dto.getComplement());
        adresse.setVille(dto.getVille());
        adresse.setCodePostal(dto.getCodePostal());
        adresse.setLatitude(dto.getLatitude());
        adresse.setLongitude(dto.getLongitude());
        adresse.setIsDefault(dto.getIsDefault() != null && dto.getIsDefault());
        return toAdresseDTO(adresseLivraisonRepository.save(adresse));
    }

    @Override
    @Transactional
    public void supprimerAdresse(Long userId, Long adresseId) {
        AdresseLivraison adresse = adresseLivraisonRepository.findByIdAndUserId(adresseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adresse introuvable"));
        adresseLivraisonRepository.delete(adresse);
    }

    @Override
    @Transactional
    public AdresseLivraisonDTO definirAdresseParDefaut(Long userId, Long adresseId) {
        adresseLivraisonRepository.clearDefaultForUser(userId);
        AdresseLivraison adresse = adresseLivraisonRepository.findByIdAndUserId(adresseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adresse introuvable"));
        adresse.setIsDefault(true);
        return toAdresseDTO(adresseLivraisonRepository.save(adresse));
    }

    @Override
    @Transactional
    public void supprimerCompte(Long userId) {
        User user = findUser(userId);
        // Save original email before anonymization for confirmation email
        String originalEmail = user.getEmail();
        String originalNom = user.getNom();

        // RGPD anonymization
        user.setEmail("deleted_" + userId + "@anonymized.mysugu");
        user.setNom("Utilisateur");
        user.setPrenom("Supprimé");
        user.setTelephone(null);
        user.setAvatar(null);
        user.setLocalisation(null);
        user.setIsActive(false);
        user.setIsDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);

        refreshTokenRepository.revokeAllUserTokens(userId, LocalDateTime.now());
        emailService.envoyerConfirmationSuppressionCompte(originalEmail, originalNom);
    }

    private String creerRefreshToken(User user) {
        String token = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Erreur SHA-256", e);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
    }

    private AdresseLivraisonDTO toAdresseDTO(AdresseLivraison a) {
        AdresseLivraisonDTO dto = new AdresseLivraisonDTO();
        dto.setId(a.getId());
        dto.setLibelle(a.getLibelle());
        dto.setAdresse(a.getAdresse());
        dto.setComplement(a.getComplement());
        dto.setVille(a.getVille());
        dto.setCodePostal(a.getCodePostal());
        dto.setLatitude(a.getLatitude());
        dto.setLongitude(a.getLongitude());
        dto.setIsDefault(a.getIsDefault());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }
}
