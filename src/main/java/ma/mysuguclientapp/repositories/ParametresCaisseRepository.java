package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.ParametresCaisse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParametresCaisseRepository extends JpaRepository<ParametresCaisse, Long> {
    // findById(1L) = get singleton
}
