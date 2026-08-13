package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.CategorieRestaurant;
import ma.mysuguclientapp.enumerations.Vertical;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoriesRestaurantRepository extends JpaRepository<CategorieRestaurant, Long> {
    Optional<CategorieRestaurant> findByNom(String nom);

    /** Null est traité comme RESTAURANT : les catégories historiques restent des catégories de restaurant. */
    @Query("SELECT c FROM CategorieRestaurant c WHERE " +
            "(:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND c.vertical IS NULL) " +
            "OR c.vertical = :vertical")
    List<CategorieRestaurant> findByVerticalEffectif(@Param("vertical") Vertical vertical);
}
