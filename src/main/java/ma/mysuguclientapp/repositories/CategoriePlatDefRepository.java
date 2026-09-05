package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.CategoriePlatDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriePlatDefRepository extends JpaRepository<CategoriePlatDef, Long> {
    List<CategoriePlatDef> findByActifTrueOrderByOrdreAsc();
    List<CategoriePlatDef> findAllByOrderByOrdreAsc();
    boolean existsByCodeIgnoreCase(String code);
    Optional<CategoriePlatDef> findByCodeIgnoreCase(String code);
}