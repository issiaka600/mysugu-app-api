package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByRestaurantsId(Long restaurantId);

    List<Promotion> findByIsActiveTrue();

    /** Promotions encore activées et non expirées côté date suivant l'API : reste à appliquer
     *  la fenêtre de début (dateDebut) et le plafond d'usage côté service (Promotion.isActiveNow). */
    List<Promotion> findByIsActiveTrueAndDateFinAfter(LocalDateTime now);

    List<Promotion> findByEstFlashTrueAndIsActiveTrueAndDateFinAfter(LocalDateTime now);
}
