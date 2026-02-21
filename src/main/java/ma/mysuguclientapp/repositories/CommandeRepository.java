package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.enumerations.StatutCommande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommandeRepository extends JpaRepository<Commande, Long> {
    Optional<Commande> findByNumeroCommande(String numeroCommande);
    Page<Commande> findByClientId(Long clientId, Pageable pageable);
    Page<Commande> findByRestaurantId(Long restaurantId, Pageable pageable);
    Page<Commande> findByStatut(StatutCommande statut, Pageable pageable);
    Page<Commande> findByClientIdAndStatut(Long clientId, StatutCommande statut, Pageable pageable);
    Page<Commande> findByRestaurantIdAndStatut(Long restaurantId, StatutCommande statut, Pageable pageable);
    List<Commande> findByClientIdOrderByCreatedAtDesc(Long clientId);
    List<Commande> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<Commande> findByLivreurIdOrderByCreatedAtDesc(Long livreurId);
    List<Commande> findByStatutInOrderByCreatedAtDesc(List<StatutCommande> statuts);
}
