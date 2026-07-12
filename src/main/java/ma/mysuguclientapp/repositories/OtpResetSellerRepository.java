package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.OtpResetSeller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpResetSellerRepository extends JpaRepository<OtpResetSeller, Long> {

    Optional<OtpResetSeller> findFirstByIdentityAndCodeAndUsedAtIsNullOrderByCreatedAtDesc(String identity, String code);

    Optional<OtpResetSeller> findFirstByIdentityAndUsedAtIsNullOrderByCreatedAtDesc(String identity);

    void deleteByIdentity(String identity);
}
