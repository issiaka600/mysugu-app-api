package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.DeviceToken;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.PlatformType;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.DeviceTokenRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.DeviceTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeviceTokenServiceImpl implements DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    @Override
    public void registerToken(Long userId, String token, String platform) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé: " + userId));

        PlatformType platformType;
        try {
            platformType = PlatformType.valueOf(platform.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Plateforme invalide: " + platform + ". Valeurs acceptées: ANDROID, IOS, WEB");
        }

        Optional<DeviceToken> existing = deviceTokenRepository.findByUserIdAndToken(userId, token);
        if (existing.isPresent()) {
            DeviceToken dt = existing.get();
            dt.setIsActive(true);
            dt.setPlatform(platformType);
            deviceTokenRepository.save(dt);
            log.info("Token FCM réactivé pour l'utilisateur {} (platform: {})", userId, platformType);
        } else {
            DeviceToken deviceToken = DeviceToken.builder()
                    .user(user)
                    .token(token)
                    .platform(platformType)
                    .isActive(true)
                    .build();
            deviceTokenRepository.save(deviceToken);
            log.info("Nouveau token FCM enregistré pour l'utilisateur {} (platform: {})", userId, platformType);
        }
    }

    @Override
    public void deactivateToken(String token) {
        deviceTokenRepository.deactivateByToken(token);
        log.info("Token FCM désactivé: {}", token);
    }

    @Override
    public void deactivateAllTokensForUser(Long userId) {
        deviceTokenRepository.deactivateAllByUserId(userId);
        log.info("Tous les tokens FCM désactivés pour l'utilisateur {}", userId);
    }
}
