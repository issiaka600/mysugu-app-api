package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByUserIdAndIsActiveTrue(Long userId);

    Optional<DeviceToken> findByToken(String token);

    Optional<DeviceToken> findByUserIdAndToken(Long userId, String token);

    @Modifying
    @Query("UPDATE DeviceToken dt SET dt.isActive = false WHERE dt.token = :token")
    void deactivateByToken(@Param("token") String token);

    @Modifying
    @Query("UPDATE DeviceToken dt SET dt.isActive = false WHERE dt.user.id = :userId")
    void deactivateAllByUserId(@Param("userId") Long userId);
}
