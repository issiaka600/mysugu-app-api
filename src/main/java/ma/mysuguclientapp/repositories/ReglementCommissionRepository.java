package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.ReglementCommission;
import ma.mysuguclientapp.enumerations.StatutReglementCommission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReglementCommissionRepository extends JpaRepository<ReglementCommission, Long> {

    /** Règlement exact couvrant une période (pour le statut d'une période demandée). */
    Optional<ReglementCommission> findByLivreurIdAndStartDateAndEndDate(Long livreurId, LocalDate startDate, LocalDate endDate);

    /** Règlements PAID/VALIDATED chevauchant une plage — pour classer les jours payés/non payés. */
    @Query("SELECT r FROM ReglementCommission r WHERE r.livreur.id = :livreurId " +
           "AND r.statut IN :statuts AND r.startDate <= :fin AND r.endDate >= :debut")
    List<ReglementCommission> findChevauchant(Long livreurId, LocalDate debut, LocalDate fin, List<StatutReglementCommission> statuts);
}
