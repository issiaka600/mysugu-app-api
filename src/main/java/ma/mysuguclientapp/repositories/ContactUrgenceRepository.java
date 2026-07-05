package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.ContactUrgence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactUrgenceRepository extends JpaRepository<ContactUrgence, Long> {

    /** Contacts actifs d'un restaurant + contacts globaux (restaurant null). */
    @Query("SELECT c FROM ContactUrgence c WHERE c.actif = true AND (c.restaurant.id = :restaurantId OR c.restaurant IS NULL)")
    List<ContactUrgence> findActifsPourRestaurant(Long restaurantId);

    List<ContactUrgence> findByActifTrue();
}
