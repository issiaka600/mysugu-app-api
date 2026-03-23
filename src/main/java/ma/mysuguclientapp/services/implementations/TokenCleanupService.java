package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.repositories.RefreshTokenRepository;
import ma.mysuguclientapp.repositories.TokenBlacklistRepository;
import ma.mysuguclientapp.repositories.TokenVerificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupService {

    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenVerificationRepository tokenVerificationRepository;

    @Scheduled(cron = "0 0 2 * * *") // Every day at 2 AM
    @Transactional
    public void nettoyerTokensExpires() {
        LocalDateTime now = LocalDateTime.now();
        tokenBlacklistRepository.deleteExpiredTokens(now);
        refreshTokenRepository.deleteExpiredTokens(now);
        tokenVerificationRepository.deleteExpiredTokens(now);
        log.info("Nettoyage des tokens expirés effectué");
    }
}
