package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.HistoriqueGpsLivraison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HistoriqueGpsLivraisonRepository extends JpaRepository<HistoriqueGpsLivraison, Long> {

    /** Dernier point GPS d'une commande (GET last-location). */
    Optional<HistoriqueGpsLivraison> findFirstByCommandeIdOrderByPointAtDesc(Long commandeId);

    /** Trajet complet ordonné (GET order-delivery-history). */
    List<HistoriqueGpsLivraison> findByCommandeIdOrderByPointAtAsc(Long commandeId);
}
