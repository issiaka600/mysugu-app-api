package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.AlerteCommandeVendeur;
import ma.mysuguclientapp.enumerations.StatutAlerteCommandeVendeur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AlerteCommandeVendeurRepository extends JpaRepository<AlerteCommandeVendeur, Long> {
    Optional<AlerteCommandeVendeur> findByCommandeId(Long commandeId);

    @Query("SELECT a.id FROM AlerteCommandeVendeur a " +
            "WHERE a.statut = :statut AND a.nextAttemptAt <= :now ORDER BY a.nextAttemptAt")
    List<Long> findDueIds(@Param("statut") StatutAlerteCommandeVendeur statut, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AlerteCommandeVendeur a WHERE a.id = :id")
    Optional<AlerteCommandeVendeur> findByIdForUpdate(@Param("id") Long id);
}
