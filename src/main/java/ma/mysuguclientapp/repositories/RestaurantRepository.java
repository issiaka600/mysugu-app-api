package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    Page<Restaurant> findByIsActive(Boolean isActive, Pageable pageable);
    Page<Restaurant> findByCategorieIdAndIsActive(Long categorieId, Boolean isActive, Pageable pageable);
    List<Restaurant> findByIsActiveOrderByAppreciationDesc(Boolean isActive);
    List<Restaurant> findByIsActive(Boolean isActive);

    @Query("SELECT r FROM Restaurant r WHERE r.isActive = true AND " +
            "(LOWER(r.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Restaurant> searchByKeyword(@Param("keyword") String keyword);
}
