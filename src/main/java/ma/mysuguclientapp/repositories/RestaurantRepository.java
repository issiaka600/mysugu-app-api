package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Promotion;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.StatutRestaurant;
import ma.mysuguclientapp.enumerations.Vertical;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    Optional<Restaurant> findByOwnerId(Long ownerId);
    List<Restaurant> findByPromotion(Promotion promotion);
    long countByZoneDeploiementId(Long zoneDeploiementId);
    Page<Restaurant> findByIsActive(Boolean isActive, Pageable pageable);
    Page<Restaurant> findByCategorieIdAndIsActive(Long categorieId, Boolean isActive, Pageable pageable);
    Page<Restaurant> findByVerticalAndIsActive(Vertical vertical, Boolean isActive, Pageable pageable);
    List<Restaurant> findByIsActiveOrderByAppreciationDesc(Boolean isActive);
    List<Restaurant> findByIsActive(Boolean isActive);
    List<Restaurant> findByStatutApprobationInOrderByDateRevueAscIdAsc(java.util.Collection<StatutRestaurant> statuts);

    @Query("SELECT r FROM Restaurant r WHERE r.isActive = true AND " +
            "(LOWER(r.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Restaurant> searchByKeyword(@Param("keyword") String keyword);
}
