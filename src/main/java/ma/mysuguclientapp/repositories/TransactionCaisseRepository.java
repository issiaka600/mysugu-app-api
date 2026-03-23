package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.TransactionCaisse;
import ma.mysuguclientapp.enumerations.TypeTransactionCaisse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface TransactionCaisseRepository extends JpaRepository<TransactionCaisse, Long> {

    Page<TransactionCaisse> findByCaisseLivreurIdOrderByCreatedAtDesc(Long caisseId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.montant), 0) FROM TransactionCaisse t " +
           "WHERE t.caisseLivreur.id = :caisseId AND t.type = :type " +
           "AND (:depuis IS NULL OR t.createdAt >= :depuis)")
    BigDecimal sumMontantByTypeEtPeriode(Long caisseId, TypeTransactionCaisse type, LocalDateTime depuis);
}
