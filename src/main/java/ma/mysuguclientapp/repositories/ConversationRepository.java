package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Retire after prod cutover (still read by ChatMigrationRunner)
@Deprecated
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** Conversations d'un client (vue "acheteur"), plus récentes d'abord */
    List<Conversation> findByClientIdOrderByDernierMessageAtDesc(Long clientId);

    /** Conversations dont le restaurant appartient à ce propriétaire (vue "vendeur") */
    List<Conversation> findByRestaurant_Owner_IdOrderByDernierMessageAtDesc(Long ownerId);

    /** Toutes les conversations d'un restaurant précis */
    List<Conversation> findByRestaurantIdOrderByDernierMessageAtDesc(Long restaurantId);

    Optional<Conversation> findByClientIdAndRestaurantId(Long clientId, Long restaurantId);
}