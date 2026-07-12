package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.ConversationUnifiee;
import ma.mysuguclientapp.enumerations.ParticipantType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ConversationUnifieeRepository extends JpaRepository<ConversationUnifiee, Long> {

    @Query("select c from ConversationUnifiee c where " +
           "(c.partyAType=:aT and c.partyAId=:aId and c.partyBType=:bT and c.partyBId=:bId)")
    Optional<ConversationUnifiee> findByParties(@Param("aT") ParticipantType aT, @Param("aId") Long aId,
                                                @Param("bT") ParticipantType bT, @Param("bId") Long bId);

    @Query("select c from ConversationUnifiee c where " +
           "(c.partyAType=:t and c.partyAId=:id) or (c.partyBType=:t and c.partyBId=:id) " +
           "order by c.dernierMessageAt desc nulls last")
    List<ConversationUnifiee> findByParticipant(@Param("t") ParticipantType t, @Param("id") Long id);
}
