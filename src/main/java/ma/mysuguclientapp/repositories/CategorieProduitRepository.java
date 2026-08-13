package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.CategorieProduit;
import ma.mysuguclientapp.enumerations.Vertical;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategorieProduitRepository extends JpaRepository<CategorieProduit, Long> {
    List<CategorieProduit> findByVerticalAndActifTrueOrderByOrdreAsc(Vertical vertical);
    List<CategorieProduit> findByVerticalOrderByOrdreAsc(Vertical vertical);
    boolean existsByVerticalAndCode(Vertical vertical, String code);
    Optional<CategorieProduit> findByVerticalAndCode(Vertical vertical, String code);
}
