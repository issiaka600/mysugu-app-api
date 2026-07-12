package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.LigneCommande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LigneCommandeRepository extends JpaRepository<LigneCommande, Long> {

    /**
     * Agrégation build-minimal pour le shim vendeur top-selling/most-popular
     * (docs/superpowers/plans/2026-07-10-vendor-3c-products.md 3c.8) : quantité totale vendue
     * par plat, pour un restaurant donné, triée décroissante. Chaque ligne = [platId, sumQuantite].
     */
    @Query("select l.plat.id, sum(l.quantite) from LigneCommande l " +
            "where l.plat.restaurant.id = :restaurantId " +
            "group by l.plat.id order by sum(l.quantite) desc")
    List<Object[]> sumQuantiteByRestaurantGroupByPlat(@Param("restaurantId") Long restaurantId);
}
