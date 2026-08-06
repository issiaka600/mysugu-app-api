package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.MessageUnifie;
import ma.mysuguclientapp.enumerations.ParticipantType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MessageUnifieRepository extends JpaRepository<MessageUnifie, Long> {
    List<MessageUnifie> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /**
     * Non-lus destinés à {@code me}. Le NOT porte sur le couple (type,id) : deux rôles peuvent
     * légitimement avoir le même identifiant numérique.
     */
    @Query("select count(m) from MessageUnifie m where m.conversationId = :conversationId " +
            "and m.seen = false and not (m.expediteurType = :expediteurType and m.expediteurId = :expediteurId)")
    long countUnseenForParticipant(@Param("conversationId") Long conversationId,
                                   @Param("expediteurType") ParticipantType expediteurType,
                                   @Param("expediteurId") Long expediteurId);
}
