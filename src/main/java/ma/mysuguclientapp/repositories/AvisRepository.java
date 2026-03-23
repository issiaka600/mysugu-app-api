package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Avis;
import ma.mysuguclientapp.enumerations.StatutAvis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AvisRepository extends JpaRepository<Avis, Long> {

    Optional<Avis> findByCommandeId(Long commandeId);

    List<Avis> findByRestaurantIdAndStatutOrderByCreatedAtDesc(Long restaurantId, StatutAvis statut);

    List<Avis> findByLivreurIdAndStatutOrderByCreatedAtDesc(Long livreurId, StatutAvis statut);

    List<Avis> findByAuteurIdOrderByCreatedAtDesc(Long auteurId);

    List<Avis> findByStatutOrderByCreatedAtDesc(StatutAvis statut);

    boolean existsByCommandeId(Long commandeId);

    @Query("SELECT AVG(a.noteRestaurant) FROM Avis a WHERE a.restaurant.id = :restaurantId AND a.statut = 'APPROUVE' AND a.noteRestaurant IS NOT NULL")
    Double getAverageNoteRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(a) FROM Avis a WHERE a.restaurant.id = :restaurantId AND a.statut = 'APPROUVE'")
    Long countApprouvesByRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT AVG(a.noteLivreur) FROM Avis a WHERE a.livreur.id = :livreurId AND a.statut = 'APPROUVE' AND a.noteLivreur IS NOT NULL")
    Double getAverageNoteLivreur(@Param("livreurId") Long livreurId);
}
