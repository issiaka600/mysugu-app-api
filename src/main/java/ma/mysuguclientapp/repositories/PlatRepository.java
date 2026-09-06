package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.enumerations.Vertical;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlatRepository extends JpaRepository<Plat, Long> {
    Page<Plat> findByRestaurantId(Long restaurantId, Pageable pageable);
    List<Plat> findByRestaurantId(Long restaurantId);
    List<Plat> findByRestaurantIdAndCategoriePlat(Long restaurantId, String categoriePlat);
    List<Plat> findByRestaurantIdAndIsAvailable(Long restaurantId, Boolean isAvailable);
    Page<Plat> findByIsAvailable(Boolean isAvailable, Pageable pageable);
    List<Plat> findByCategoriePlat(String categoriePlat);
    Page<Plat> findByRestaurantIdAndCategoriePlatAndIsAvailable(
            Long restaurantId, String categoriePlat, Boolean isAvailable, Pageable pageable);

    @Query("SELECT p FROM Plat p WHERE " +
            "(LOWER(p.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Plat> searchByKeyword(@Param("keyword") String keyword);

    /**
     * Listing paginé filtré en base. Tous les critères sont optionnels (null = pas de filtre),
     * sauf la verticale : null y signifie « toutes verticales », l'aiguillage RESTAURANT-par-défaut
     * étant fait par le service.
     */
    @Query("SELECT p FROM Plat p WHERE " +
            "(:restaurantId IS NULL OR p.restaurant.id = :restaurantId) AND " +
            "(:categoriePlat IS NULL OR p.categoriePlat = :categoriePlat) AND " +
            "(:categorieProduit IS NULL OR p.categorieProduit = :categorieProduit) AND " +
            "(:vertical IS NULL OR " +
            " (:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND p.restaurant.vertical IS NULL) " +
            " OR p.restaurant.vertical = :vertical) " +
            "ORDER BY p.id")
    Page<Plat> rechercheFiltree(@Param("restaurantId") Long restaurantId,
                                @Param("categoriePlat") String categoriePlat,
                                @Param("categorieProduit") String categorieProduit,
                                @Param("vertical") Vertical vertical,
                                Pageable pageable);

    @Query("SELECT p FROM Plat p WHERE " +
            "(LOWER(p.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:vertical IS NULL OR " +
            " (:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND p.restaurant.vertical IS NULL) " +
            " OR p.restaurant.vertical = :vertical)")
    List<Plat> searchByKeywordAndVertical(@Param("keyword") String keyword,
                                          @Param("vertical") Vertical vertical);

    /**
     * Charge un plat en verrouillant sa ligne jusqu'à la fin de la transaction.
     * Utilisé uniquement pour le décrément de stock, afin d'empêcher la survente concurrente.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Plat p WHERE p.id = :id")
    java.util.Optional<Plat> findByIdForUpdate(@Param("id") Long id);

    /** Codes de rayon (categorieProduit) distincts effectivement utilisés par les produits d'un établissement. */
    @Query("SELECT DISTINCT p.categorieProduit FROM Plat p " +
            "WHERE p.restaurant.id = :restaurantId AND p.categorieProduit IS NOT NULL")
    List<String> findRayonsUtilises(@Param("restaurantId") Long restaurantId);

    /** Nombre de plats référençant chaque catégorie de plat (code -> count). */
    @Query("SELECT p.categoriePlat, COUNT(p) FROM Plat p WHERE p.categoriePlat IS NOT NULL GROUP BY p.categoriePlat")
    List<Object[]> countPlatsParCategoriePlat();
}
