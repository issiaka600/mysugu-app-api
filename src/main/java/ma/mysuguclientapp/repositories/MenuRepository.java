package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findByRestaurantIdAndIsActiveTrueOrderByNomAsc(Long restaurantId);

    List<Menu> findByRestaurantId(Long restaurantId);
}
