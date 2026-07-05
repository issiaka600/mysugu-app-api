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

    /** Somme all-time par type (total_deposit legacy). Sans param date pour eviter le
     *  "could not determine data type of parameter" de Postgres sur un bind null. */
    @Query("SELECT COALESCE(SUM(t.montant), 0) FROM TransactionCaisse t " +
           "WHERE t.caisseLivreur.id = :caisseId AND t.type = :type")
    BigDecimal sumMontantByCaisseAndType(Long caisseId, TypeTransactionCaisse type);

    // --- Legacy livreur (collected_cash_history = remises plateforme) ---
    Page<TransactionCaisse> findByCaisseLivreurIdAndTypeOrderByCreatedAtDesc(
            Long caisseId, TypeTransactionCaisse type, Pageable pageable);

    Page<TransactionCaisse> findByCaisseLivreurIdAndTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long caisseId, TypeTransactionCaisse type, LocalDateTime debut, LocalDateTime fin, Pageable pageable);
}
