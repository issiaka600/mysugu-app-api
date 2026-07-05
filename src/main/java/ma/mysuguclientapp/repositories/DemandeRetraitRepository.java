package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.DemandeRetrait;
import ma.mysuguclientapp.enumerations.StatutRetrait;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface DemandeRetraitRepository extends JpaRepository<DemandeRetrait, Long> {

    Page<DemandeRetrait> findByLivreurIdAndStatutOrderByCreatedAtDesc(Long livreurId, StatutRetrait statut, Pageable pageable);

    Page<DemandeRetrait> findByLivreurIdAndStatutAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long livreurId, StatutRetrait statut, LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    @Query("SELECT COALESCE(SUM(d.montant), 0) FROM DemandeRetrait d WHERE d.livreur.id = :livreurId AND d.statut = :statut")
    BigDecimal sumMontantByLivreurAndStatut(Long livreurId, StatutRetrait statut);
}
