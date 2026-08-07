package ma.mysuguclientapp.chat;

import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ConversationRepositoryTest {
    @Autowired ConversationUnifieeRepository convs;
    @Autowired MessageUnifieRepository msgs;

    @Test
    void findByParties_and_unseen_count() {
        var c = convs.save(ConversationUnifiee.builder()
            .partyAType(ParticipantType.CUSTOMER).partyAId(7L)
            .partyBType(ParticipantType.RESTAURANT).partyBId(3L)
            .dernierMessageAt(LocalDateTime.now()).build());
        msgs.save(MessageUnifie.builder().conversationId(c.getId())
            .expediteurType(ParticipantType.RESTAURANT).expediteurId(3L)
            .contenu("bonjour").seen(false).build());

        assertTrue(convs.findByParties(ParticipantType.CUSTOMER, 7L, ParticipantType.RESTAURANT, 3L).isPresent());
        // customer 7 has 1 unseen (sent by restaurant)
        long unseen = msgs.countUnseenForParticipant(
            c.getId(), ParticipantType.CUSTOMER, 7L);
        assertEquals(1, unseen);
    }
}
