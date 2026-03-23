package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.PointsFidelite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PointsFideliteRepository extends JpaRepository<PointsFidelite, Long> {

    Optional<PointsFidelite> findByUserId(Long userId);
}
