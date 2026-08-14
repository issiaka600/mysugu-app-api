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

    /**
     * Restaurant "principal" d'un propriétaire. Un owner peut posséder plusieurs restaurants
     * (données réelles constatées) : on renvoie donc UN seul enregistrement déterministe plutôt
     * que de laisser une requête dérivée lever {@code NonUniqueResultException} (500).
     * Priorité : actif d'abord, puis approuvé, puis le plus récent (id décroissant).
     */
    @Query(value = "SELECT * FROM restaurants r WHERE r.owner_id = :ownerId " +
            "ORDER BY r.is_active DESC NULLS LAST, " +
            "(r.statut_approbation = 'APPROUVE') DESC NULLS LAST, " +
            "r.id DESC LIMIT 1", nativeQuery = true)
    Optional<Restaurant> findByOwnerId(@Param("ownerId") Long ownerId);
    List<Restaurant> findByPromotion(Promotion promotion);
    long countByZoneDeploiementId(Long zoneDeploiementId);
    Page<Restaurant> findByIsActive(Boolean isActive, Pageable pageable);
    Page<Restaurant> findByCategorieIdAndIsActive(Long categorieId, Boolean isActive, Pageable pageable);
    Page<Restaurant> findByVerticalAndIsActive(Vertical vertical, Boolean isActive, Pageable pageable);
    List<Restaurant> findByVerticalAndIsActive(Vertical vertical, Boolean isActive);
    List<Restaurant> findByIsActiveOrderByAppreciationDesc(Boolean isActive);
    List<Restaurant> findByIsActive(Boolean isActive);
    List<Restaurant> findByStatutApprobationInOrderByDateRevueAscIdAsc(java.util.Collection<StatutRestaurant> statuts);

    @Query("SELECT r FROM Restaurant r WHERE r.isActive = true AND " +
            "(LOWER(r.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Restaurant> searchByKeyword(@Param("keyword") String keyword);

    /** Recherche filtrée par verticale. Null en base est traité comme RESTAURANT. */
    @Query("SELECT r FROM Restaurant r WHERE r.isActive = true AND " +
            "(LOWER(r.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "((:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND r.vertical IS NULL) " +
            " OR r.vertical = :vertical)")
    List<Restaurant> searchByKeywordAndVertical(@Param("keyword") String keyword,
                                                @Param("vertical") Vertical vertical);

    @Query("SELECT r FROM Restaurant r WHERE r.isActive = true AND " +
            "((:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND r.vertical IS NULL) " +
            " OR r.vertical = :vertical) ORDER BY r.appreciation DESC")
    List<Restaurant> findByVerticalOrderByAppreciationDesc(@Param("vertical") Vertical vertical);
}
