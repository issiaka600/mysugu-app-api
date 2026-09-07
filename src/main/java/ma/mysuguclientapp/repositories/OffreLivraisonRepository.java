package ma.mysuguclientapp.repositories;

import jakarta.persistence.LockModeType;
import ma.mysuguclientapp.entities.OffreLivraison;
import ma.mysuguclientapp.enumerations.StatutOffreLivraison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OffreLivraisonRepository extends JpaRepository<OffreLivraison, Long> {
    Optional<OffreLivraison> findByCommandeIdAndStatut(Long commandeId, StatutOffreLivraison statut);
    Optional<OffreLivraison> findByCommandeIdAndLivreurIdAndStatut(Long commandeId, Long livreurId, StatutOffreLivraison statut);
    Optional<OffreLivraison> findFirstByCommandeIdAndLivreurIdAndStatutOrderByRespondedAtDesc(
            Long commandeId, Long livreurId, StatutOffreLivraison statut);
    List<OffreLivraison> findByLivreurIdAndStatutIn(Long livreurId, Collection<StatutOffreLivraison> statuts);
    List<OffreLivraison> findByCommandeIdOrderBySequenceNumberAsc(Long commandeId);
    long countByCommandeId(Long commandeId);
    boolean existsByLivreurIdAndStatut(Long livreurId, StatutOffreLivraison statut);

    @Query("SELECT o.id FROM OffreLivraison o WHERE o.statut = :statut AND o.expiresAt <= :now")
    List<Long> findExpiredIds(@Param("statut") StatutOffreLivraison statut, @Param("now") LocalDateTime now);

    @Query("SELECT o.id FROM OffreLivraison o WHERE o.statut = :statut " +
            "AND o.nextAlertAt <= :now AND o.expiresAt > :now")
    List<Long> findAlertDueIds(@Param("statut") StatutOffreLivraison statut, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OffreLivraison o WHERE o.commande.id = :commandeId AND o.livreur.id = :livreurId AND o.statut = :statut")
    Optional<OffreLivraison> findForUpdate(@Param("commandeId") Long commandeId,
                                            @Param("livreurId") Long livreurId,
                                            @Param("statut") StatutOffreLivraison statut);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OffreLivraison o WHERE o.id = :id")
    Optional<OffreLivraison> findByIdForUpdate(@Param("id") Long id);

    boolean existsByCommandeIdAndLivreurId(Long commandeId, Long livreurId);
}
