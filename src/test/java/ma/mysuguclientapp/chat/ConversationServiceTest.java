package ma.mysuguclientapp.chat;

import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.repositories.*;
import ma.mysuguclientapp.services.chat.*;
import ma.mysuguclientapp.services.interfaces.NotificationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ConversationService.class, ParticipantResolver.class})
class ConversationServiceTest {
    @Autowired ConversationService service;
    @Autowired ConversationUnifieeRepository convs;
    @MockitoBean NotificationService notifications; // FCM boundary
    @MockitoBean RestaurantRepository restaurantRepository;
    @MockitoBean UserRepository userRepository;

    ParticipantRef customer = new ParticipantRef(ParticipantType.CUSTOMER, 7L);
    ParticipantRef livreur  = new ParticipantRef(ParticipantType.LIVREUR, 5L);

    @Test
    void append_creates_one_conversation_per_pair_regardless_of_direction() {
        service.append(customer, livreur, "hi", List.of());
        service.append(livreur, customer, "yo", List.of());
        assertEquals(1, convs.count());
    }

    @Test
    void thread_marks_incoming_seen_and_unseen_drops_to_zero() {
        service.append(livreur, customer, "ping", List.of()); // sent by livreur
        var c = convs.findAll().get(0);
        assertEquals(1, service.unseenCount(c, customer));
        service.thread(customer, livreur);                    // customer reads
        assertEquals(0, service.unseenCount(c, customer));
    }

    @Test
    void append_notifies_recipient_user() {
        service.append(customer, livreur, "hi", List.of());
        verify(notifications).envoyerNotification(eq(5L), anyString(), eq("hi"),
            eq(ma.mysuguclientapp.enumerations.TypeNotification.MESSAGE), anyLong(), eq("CONVERSATION"));
    }

    @Test
    void append_with_message_over_500_chars_succeeds_and_truncates_only_the_summary() {
        String longContenu = "a".repeat(1800); // > 500 (dernier_message) but <= 2000 (contenu)

        MessageUnifie saved = assertDoesNotThrow(() -> service.append(customer, livreur, longContenu, List.of()));

        assertEquals(longContenu, saved.getContenu());
        assertEquals(1800, saved.getContenu().length());

        var c = convs.findAll().get(0);
        assertEquals(500, c.getDernierMessage().length());
        assertEquals(longContenu.substring(0, 500), c.getDernierMessage());
    }
}
