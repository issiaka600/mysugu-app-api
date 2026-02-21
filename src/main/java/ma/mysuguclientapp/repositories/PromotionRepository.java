package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
}
