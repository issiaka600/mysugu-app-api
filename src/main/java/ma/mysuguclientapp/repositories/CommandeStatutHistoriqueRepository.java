package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.CommandeStatutHistorique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommandeStatutHistoriqueRepository extends JpaRepository<CommandeStatutHistorique, Long> {
    List<CommandeStatutHistorique> findByCommandeIdOrderByChangedAtAscIdAsc(Long commandeId);
}
