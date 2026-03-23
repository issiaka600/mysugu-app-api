package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.AdresseLivraison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdresseLivraisonRepository extends JpaRepository<AdresseLivraison, Long> {

    List<AdresseLivraison> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

    Optional<AdresseLivraison> findByIdAndUserId(Long id, Long userId);

    Optional<AdresseLivraison> findByUserIdAndIsDefaultTrue(Long userId);

    @Modifying
    @Query("UPDATE AdresseLivraison a SET a.isDefault = false WHERE a.user.id = :userId")
    void clearDefaultForUser(Long userId);
}
