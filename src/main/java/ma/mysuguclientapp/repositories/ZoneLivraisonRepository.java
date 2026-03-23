package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.ZoneLivraison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoneLivraisonRepository extends JpaRepository<ZoneLivraison, Long> {

    List<ZoneLivraison> findByRestaurantIdAndIsActiveTrueOrderByFraisLivraisonAsc(Long restaurantId);

    List<ZoneLivraison> findByRestaurantId(Long restaurantId);
}
