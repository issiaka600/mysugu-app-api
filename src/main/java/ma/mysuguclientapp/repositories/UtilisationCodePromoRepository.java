package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.UtilisationCodePromo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UtilisationCodePromoRepository extends JpaRepository<UtilisationCodePromo, Long> {

    boolean existsByCodePromoIdAndUserId(Long codePromoId, Long userId);

    boolean existsByCodePromoIdAndCommandeId(Long codePromoId, Long commandeId);
}
