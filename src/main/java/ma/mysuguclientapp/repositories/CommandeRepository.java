package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.enumerations.StatutCommande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository

public interface CommandeRepository extends JpaRepository<Commande, Long> {
    Optional<Commande> findByNumeroCommande(String numeroCommande);
    Page<Commande> findByClientId(Long clientId, Pageable pageable);
    Page<Commande> findByRestaurantId(Long restaurantId, Pageable pageable);
    Page<Commande> findByStatut(StatutCommande statut, Pageable pageable);
    Page<Commande> findByClientIdAndStatut(Long clientId, StatutCommande statut, Pageable pageable);
    Page<Commande> findByRestaurantIdAndStatut(Long restaurantId, StatutCommande statut, Pageable pageable);
    List<Commande> findByClientIdOrderByCreatedAtDesc(Long clientId);
    List<Commande> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<Commande> findByLivreurIdOrderByCreatedAtDesc(Long livreurId);
    List<Commande> findByStatutInOrderByCreatedAtDesc(List<StatutCommande> statuts);

    @Query("SELECT COUNT(c) FROM Commande c WHERE c.restaurant.id = :restaurantId AND c.createdAt BETWEEN :debut AND :fin")
    Long countByRestaurantIdAndPeriode(Long restaurantId, LocalDateTime debut, LocalDateTime fin);

    @Query("SELECT COUNT(c) FROM Commande c WHERE c.restaurant.id = :restaurantId AND c.statut = :statut AND c.createdAt BETWEEN :debut AND :fin")
    Long countByRestaurantIdAndStatutAndPeriode(Long restaurantId, StatutCommande statut, LocalDateTime debut, LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(c.montantTotal), 0) FROM Commande c WHERE c.restaurant.id = :restaurantId AND c.statut = 'LIVREE' AND c.createdAt BETWEEN :debut AND :fin")
    BigDecimal sumChiffreAffairesRestaurant(Long restaurantId, LocalDateTime debut, LocalDateTime fin);

    @Query("SELECT COUNT(c) FROM Commande c WHERE c.restaurant.id = :restaurantId AND c.statut IN ('EN_PREPARATION', 'CONFIRMEE')")
    Long countEnCoursRestaurant(Long restaurantId);

    Page<Commande> findByClientIdOrderByCreatedAtDesc(Long clientId, Pageable pageable);

    List<Commande> findTop5ByClientIdOrderByCreatedAtDesc(Long clientId);
}
