package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.PreuveLivraison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PreuveLivraisonRepository extends JpaRepository<PreuveLivraison, Long> {

    List<PreuveLivraison> findByCommandeIdOrderByCreatedAtAsc(Long commandeId);
}
