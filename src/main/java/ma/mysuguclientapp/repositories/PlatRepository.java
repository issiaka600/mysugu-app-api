package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.enumerations.CategoriePlat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlatRepository extends JpaRepository<Plat, Long> {
    Page<Plat> findByRestaurantId(Long restaurantId, Pageable pageable);
    List<Plat> findByRestaurantIdAndIsAvailable(Long restaurantId, Boolean isAvailable);
    Page<Plat> findByIsAvailable(Boolean isAvailable, Pageable pageable);
    Page<Plat> findByRestaurantIdAndCategoriePlatAndIsAvailable(
            Long restaurantId, CategoriePlat categoriePlat, Boolean isAvailable, Pageable pageable);

    @Query("SELECT p FROM Plat p WHERE " +
            "(LOWER(p.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Plat> searchByKeyword(@Param("keyword") String keyword);
}
