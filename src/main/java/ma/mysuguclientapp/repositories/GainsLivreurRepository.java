package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.GainsLivreur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface GainsLivreurRepository extends JpaRepository<GainsLivreur, Long> {

    Page<GainsLivreur> findByLivreurIdOrderByCreatedAtDesc(Long livreurId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(g.montantNet), 0) FROM GainsLivreur g WHERE g.livreur.id = :livreurId")
    BigDecimal sumGainsByLivreurId(Long livreurId);

    @Query("SELECT COALESCE(SUM(g.montantNet), 0) FROM GainsLivreur g WHERE g.livreur.id = :livreurId " +
           "AND g.createdAt BETWEEN :debut AND :fin")
    BigDecimal sumGainsByLivreurIdAndPeriode(Long livreurId, LocalDateTime debut, LocalDateTime fin);

    @Query("SELECT COUNT(g) FROM GainsLivreur g WHERE g.livreur.id = :livreurId")
    Long countByLivreurId(Long livreurId);

    // --- Modèle argent legacy (shim Tiktak, techspec §7) ---

    /** Somme de TOUS les gains nets d'un livreur (base du current_balance = gains − retraits approuvés). */
    @Query("SELECT COALESCE(SUM(g.montantNet), 0) FROM GainsLivreur g WHERE g.livreur.id = :livreurId")
    BigDecimal sumMontantNetByLivreur(Long livreurId);

    /** (Conserve) somme des gains nets non encore payés — vestigial depuis le modèle current_balance = gains − retraits. */
    @Query("SELECT COALESCE(SUM(g.montantNet), 0) FROM GainsLivreur g WHERE g.livreur.id = :livreurId AND g.estPaye = false")
    BigDecimal sumMontantNetNonPayeByLivreur(Long livreurId);

    /** Gains non payés d'un livreur, plus anciens d'abord — pour les marquer payés lors d'un retrait approuvé. */
    java.util.List<GainsLivreur> findByLivreurIdAndEstPayeFalseOrderByCreatedAtAsc(Long livreurId);

    /** Empêche le double-enregistrement des gains sur une même commande (OneToOne). */
    boolean existsByCommandeId(Long commandeId);

    /**
     * Vendor shim 3e (derive-minimal): net earnings of a livreur, scoped to orders delivered for
     * ONE restaurant (not the livreur's global earnings across all restaurants). Cheap single
     * JPQL join on GainsLivreur.commande.restaurant — vendor->livreur ownership is NOT native
     * (umbrella §4 GAP; 3e SCOPE DECISION), this is a read-only derivation only.
     */
    @Query("SELECT COALESCE(SUM(g.montantNet), 0) FROM GainsLivreur g WHERE g.livreur.id = :livreurId " +
           "AND g.commande.restaurant.id = :restaurantId")
    BigDecimal sumMontantNetByLivreurAndRestaurant(Long livreurId, Long restaurantId);
}
