package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.CaisseLivreur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaisseLivreurRepository extends JpaRepository<CaisseLivreur, Long> {

    Optional<CaisseLivreur> findByLivreurId(Long livreurId);

    /** Livreurs dont le solde dépasse le plafond personnalisé ou le plafond passé en paramètre */
    @Query("SELECT c FROM CaisseLivreur c WHERE c.soldeCourant >= " +
           "COALESCE(c.plafondPersonnalise, :plafondGlobal) * :seuilPct / 100 " +
           "AND c.alertePlafondEnvoyee = false")
    List<CaisseLivreur> findDepassantSeuil(BigDecimal plafondGlobal, int seuilPct);

    /** Livreurs n'ayant pas fait de réconciliation depuis plus de N heures */
    @Query("SELECT c FROM CaisseLivreur c WHERE c.soldeCourant > 0 AND " +
           "(c.derniereReconciliation IS NULL OR c.derniereReconciliation < :avant) " +
           "AND c.alerteIntervalleEnvoyee = false")
    List<CaisseLivreur> findDepassantIntervalle(LocalDateTime avant);

    /** Tous les livreurs avec un solde > 0 (pour tableau de bord admin) */
    @Query("SELECT c FROM CaisseLivreur c WHERE c.soldeCourant > 0 ORDER BY c.soldeCourant DESC")
    List<CaisseLivreur> findAvecSoldePositif();
}
