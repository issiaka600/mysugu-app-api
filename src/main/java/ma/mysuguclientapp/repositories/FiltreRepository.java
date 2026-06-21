package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Filtre;
import ma.mysuguclientapp.enumerations.FiltreContexte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FiltreRepository extends JpaRepository<Filtre, Long> {
    List<Filtre> findByContexteAndActifTrueOrderByOrdreAsc(FiltreContexte contexte);
    List<Filtre> findByContexteOrderByOrdreAsc(FiltreContexte contexte);
}
