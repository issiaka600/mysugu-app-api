package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.TokenVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TokenVerificationRepository extends JpaRepository<TokenVerification, Long> {

    Optional<TokenVerification> findByTokenAndType(String token, String type);

    @Modifying
    @Query("DELETE FROM TokenVerification t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(LocalDateTime now);
}
