package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.DetteRestaurant;
import ma.mysuguclientapp.enumerations.StatutDetteRestaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DetteRestaurantRepository extends JpaRepository<DetteRestaurant, Long> {

    Optional<DetteRestaurant> findByCommandeId(Long commandeId);

    List<DetteRestaurant> findByRestaurantIdAndStatut(Long restaurantId, StatutDetteRestaurant statut);

    List<DetteRestaurant> findByRestaurantIdAndStatutAndCreatedAtBetween(
            Long restaurantId, StatutDetteRestaurant statut,
            LocalDateTime debut, LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(d.montantDu), 0) FROM DetteRestaurant d " +
           "WHERE d.restaurant.id = :restaurantId AND d.statut = 'EN_ATTENTE'")
    BigDecimal sumDetteEnAttenteByRestaurant(Long restaurantId);

    @Query("SELECT COALESCE(SUM(d.montantDu), 0) FROM DetteRestaurant d WHERE d.statut = 'EN_ATTENTE'")
    BigDecimal sumTotalDetteEnAttente();

    List<DetteRestaurant> findByPaiementRestaurantId(Long paiementRestaurantId);

    /** Dettes EN_ATTENTE pour tous les restaurants (vue admin) */
    @Query("SELECT d.restaurant.id, SUM(d.montantDu) FROM DetteRestaurant d " +
           "WHERE d.statut = 'EN_ATTENTE' GROUP BY d.restaurant.id")
    List<Object[]> groupSumDetteEnAttenteParRestaurant();
}
