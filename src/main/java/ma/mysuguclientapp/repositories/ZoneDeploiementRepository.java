package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.ZoneDeploiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoneDeploiementRepository extends JpaRepository<ZoneDeploiement, Long> {

    List<ZoneDeploiement> findByIsActiveTrueOrderByNomAsc();

    boolean existsByNomIgnoreCase(String nom);
}
