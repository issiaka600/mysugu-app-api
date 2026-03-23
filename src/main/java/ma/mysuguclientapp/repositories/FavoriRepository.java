package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Favori;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriRepository extends JpaRepository<Favori, Long> {

    List<Favori> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Favori> findByUserIdAndRestaurantId(Long userId, Long restaurantId);

    boolean existsByUserIdAndRestaurantId(Long userId, Long restaurantId);

    void deleteByUserIdAndRestaurantId(Long userId, Long restaurantId);

    Long countByRestaurantId(Long restaurantId);
}
