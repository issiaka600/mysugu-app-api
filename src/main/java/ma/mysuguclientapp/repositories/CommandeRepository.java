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

    /**
     * Liste filtrée des commandes ; chaque critère à {@code null} est neutre.
     *
     * <p><b>Écrite en JPQL, jamais en requête dérivée.</b> Une dérivée sur {@code vertical}
     * exclurait silencieusement les lignes {@code NULL} — c'est exactement ce qui a rendu
     * invisibles tous les établissements historiques pendant deux mois (correctif 055a848).
     * Ici, {@code vertical = RESTAURANT} rattrape explicitement
     * {@code c.restaurant.vertical IS NULL}.
     */
    @Query("SELECT c FROM Commande c WHERE " +
           "(:clientId IS NULL OR c.client.id = :clientId) AND " +
           "(:restaurantId IS NULL OR c.restaurant.id = :restaurantId) AND " +
           "(:statut IS NULL OR c.statut = :statut) AND " +
           "(:vertical IS NULL OR " +
           " (:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND c.restaurant.vertical IS NULL) " +
           " OR c.restaurant.vertical = :vertical)")
    Page<Commande> rechercheFiltree(@org.springframework.data.repository.query.Param("clientId") Long clientId,
                                    @org.springframework.data.repository.query.Param("restaurantId") Long restaurantId,
                                    @org.springframework.data.repository.query.Param("statut") StatutCommande statut,
                                    @org.springframework.data.repository.query.Param("vertical") ma.mysuguclientapp.enumerations.Vertical vertical,
                                    Pageable pageable);

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

    /** Compte des commandes d'un restaurant groupé par statut : [StatutCommande, Long]. */
    @Query("SELECT c.statut, COUNT(c) FROM Commande c WHERE c.restaurant.id = :restaurantId GROUP BY c.statut")
    List<Object[]> countByStatutGroupedForRestaurant(Long restaurantId);

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

    /** Une paire de participants peut échanger uniquement si elle est liée par une commande. */
    boolean existsByClientIdAndRestaurantId(Long clientId, Long restaurantId);
    boolean existsByClientIdAndLivreurId(Long clientId, Long livreurId);
    boolean existsByRestaurantIdAndLivreurId(Long restaurantId, Long livreurId);
    Optional<Commande> findFirstByClientIdAndRestaurantIdOrderByCreatedAtDesc(Long clientId, Long restaurantId);
    Optional<Commande> findFirstByClientIdAndLivreurIdOrderByCreatedAtDesc(Long clientId, Long livreurId);
    Optional<Commande> findFirstByRestaurantIdAndLivreurIdOrderByCreatedAtDesc(Long restaurantId, Long livreurId);

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
