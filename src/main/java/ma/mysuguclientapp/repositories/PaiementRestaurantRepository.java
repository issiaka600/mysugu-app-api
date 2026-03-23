package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.PaiementRestaurant;
import ma.mysuguclientapp.enumerations.StatutPaiementRestaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PaiementRestaurantRepository extends JpaRepository<PaiementRestaurant, Long> {

    Page<PaiementRestaurant> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    List<PaiementRestaurant> findByRestaurantIdAndStatut(Long restaurantId, StatutPaiementRestaurant statut);

    @Query("SELECT COALESCE(SUM(p.montantTotal), 0) FROM PaiementRestaurant p " +
           "WHERE p.restaurant.id = :restaurantId AND p.statut = 'EFFECTUE'")
    BigDecimal sumPaiementsEffectues(Long restaurantId);
}
