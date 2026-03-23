package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.CodePromo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CodePromoRepository extends JpaRepository<CodePromo, Long> {

    Optional<CodePromo> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT c FROM CodePromo c WHERE c.isActive = true AND c.code = :code " +
           "AND (c.dateDebut IS NULL OR c.dateDebut <= :now) " +
           "AND (c.dateFin IS NULL OR c.dateFin >= :now) " +
           "AND (c.usageMax IS NULL OR c.usageCount < c.usageMax)")
    Optional<CodePromo> findValidCode(String code, LocalDateTime now);

    List<CodePromo> findByIsActiveTrue();
}
