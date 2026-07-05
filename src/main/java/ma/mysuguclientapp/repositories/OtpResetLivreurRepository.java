package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.OtpResetLivreur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpResetLivreurRepository extends JpaRepository<OtpResetLivreur, Long> {

    Optional<OtpResetLivreur> findFirstByTelephoneAndCodeAndUsedAtIsNullOrderByCreatedAtDesc(String telephone, String code);

    Optional<OtpResetLivreur> findFirstByTelephoneAndUsedAtIsNullOrderByCreatedAtDesc(String telephone);

    void deleteByTelephone(String telephone);
}
