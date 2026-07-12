package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.MessageUnifie;
import ma.mysuguclientapp.enumerations.ParticipantType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageUnifieRepository extends JpaRepository<MessageUnifie, Long> {
    List<MessageUnifie> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    long countByConversationIdAndSeenFalseAndExpediteurTypeNotAndExpediteurIdNot(
        Long conversationId, ParticipantType expediteurType, Long expediteurId);
}
