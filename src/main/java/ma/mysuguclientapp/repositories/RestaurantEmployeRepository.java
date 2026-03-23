package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.RestaurantEmploye;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantEmployeRepository extends JpaRepository<RestaurantEmploye, Long> {

    List<RestaurantEmploye> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    Optional<RestaurantEmploye> findByRestaurantIdAndUserId(Long restaurantId, Long userId);

    boolean existsByRestaurantIdAndUserId(Long restaurantId, Long userId);

    List<RestaurantEmploye> findByUserId(Long userId);
}
