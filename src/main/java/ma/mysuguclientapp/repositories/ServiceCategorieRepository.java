package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.ServiceCategorie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceCategorieRepository extends JpaRepository<ServiceCategorie, Long> {
    List<ServiceCategorie> findByIsActiveTrueOrderByOrdreAscNomAsc();
    List<ServiceCategorie> findAllByOrderByOrdreAscNomAsc();
    Optional<ServiceCategorie> findByNom(String nom);
}
