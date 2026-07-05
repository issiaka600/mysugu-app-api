package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.MessageLivreur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageLivreurRepository extends JpaRepository<MessageLivreur, Long> {

    /** Tous les messages d'un livreur pour un type d'interlocuteur (récent d'abord) — sert à lister les conversations. */
    List<MessageLivreur> findByLivreurIdAndInterlocuteurTypeOrderByCreatedAtDesc(Long livreurId, String interlocuteurType);

    /** Fil d'une conversation précise (ancien d'abord). */
    List<MessageLivreur> findByLivreurIdAndInterlocuteurTypeAndInterlocuteurIdOrderByCreatedAtAsc(
            Long livreurId, String interlocuteurType, Long interlocuteurId);
}
