package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.TransactionPoints;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionPointsRepository extends JpaRepository<TransactionPoints, Long> {

    Page<TransactionPoints> findByPointsFideliteIdOrderByCreatedAtDesc(Long pointsFideliteId, Pageable pageable);
}
