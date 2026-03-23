package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.ParametresPaiementRestaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParametresPaiementRestaurantRepository extends JpaRepository<ParametresPaiementRestaurant, Long> {

    Optional<ParametresPaiementRestaurant> findByRestaurantId(Long restaurantId);
}
