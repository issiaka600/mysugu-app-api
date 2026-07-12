package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.User;
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
    Optional<Commande> findByTiktakOrderId(Long tiktakOrderId);
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

    @Query("SELECT COUNT(c) FROM Commande c WHERE c.restaurant.id = :restaurantId AND c.statut IN ('EN_PREPARATION', 'CONFIRMEE', 'ASSIGNEE_LIVREUR')")
    Long countEnCoursRestaurant(Long restaurantId);

    Page<Commande> findByClientIdOrderByCreatedAtDesc(Long clientId, Pageable pageable);

    List<Commande> findTop5ByClientIdOrderByCreatedAtDesc(Long clientId);

    Optional<Commande> findByStripePaymentIntentId(String stripePaymentIntentId);

    // --- Legacy livreur (shim Tiktak) ---
    long countByLivreurId(Long livreurId);
    long countByLivreurIdAndStatut(Long livreurId, StatutCommande statut);
    long countByLivreurIdAndStatutIn(Long livreurId, List<StatutCommande> statuts);
    long countByLivreurIdAndEnPauseTrue(Long livreurId);
    List<Commande> findByStatutInAndLivreurIsNullOrderByCreatedAtAsc(List<StatutCommande> statuts);
    List<Commande> findByLivreurIdAndStatutInOrderByCreatedAtDesc(Long livreurId, List<StatutCommande> statuts);

    /** Verrou pessimiste pour la revendication FCFS d'une commande (POST /accept). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Commande c WHERE c.id = :id")
    Optional<Commande> findByIdForUpdate(Long id);

    // --- Vendor shim 3e: derivation-only (vendor->livreur ownership is NOT native, umbrella §4
    // GAP; 3e SCOPE DECISION). These derive the "roster" of livreurs who actually served a given
    // restaurant, read-only, from existing Commande rows — no ownership FK is created. ---

    /** Distinct livreurs who delivered at least one order for this restaurant (the derived roster). */
    @Query("SELECT DISTINCT c.livreur FROM Commande c WHERE c.restaurant.id = :rid AND c.livreur IS NOT NULL")
    List<User> findDistinctLivreursByRestaurant(Long rid);

    /** Orders a given livreur delivered for a given restaurant (order-list/earning derivation). */
    @Query("SELECT c FROM Commande c WHERE c.livreur.id = :lid AND c.restaurant.id = :rid ORDER BY c.createdAt DESC")
    List<Commande> findByLivreurAndRestaurant(Long lid, Long rid);
}
