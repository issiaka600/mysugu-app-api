package ma.mysuguclientapp.chat;

import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.dtos.ConversationDTO;
import ma.mysuguclientapp.dtos.MessageChatCreateDTO;
import ma.mysuguclientapp.dtos.MessageChatDTO;
import ma.mysuguclientapp.entities.ConversationUnifiee;
import ma.mysuguclientapp.entities.MessageUnifie;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.MessageUnifieRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.chat.ConversationService;
import ma.mysuguclientapp.services.chat.ParticipantResolver;
import ma.mysuguclientapp.services.implementations.MessagerieServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ma.mysuguclientapp.enumerations.ParticipantType.CUSTOMER;
import static ma.mysuguclientapp.enumerations.ParticipantType.RESTAURANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Contrat du canal vendeur (/api/messages) rebranché sur le store unifié (Task 6) : la même
 * façade DTO (ConversationDTO/MessageChatDTO) doit désormais être construite à partir de
 * {@link ConversationService}, sans lecture/écriture directe dans les tables legacy
 * Conversation/MessageChat. Unitaire (Mockito), pas de contexte Spring ni de DB.
 */
class MessagerieServiceUnifiedTest {

    @Mock ConversationService chat;
    @Mock ParticipantResolver resolver;
    @Mock RestaurantRepository restaurantRepository;
    @Mock UserRepository userRepository;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock MessageUnifieRepository messageUnifieRepository;

    MessagerieServiceImpl service;

    private static final String TOKEN = "Bearer sometoken";
    private static final Long CLIENT_ID = 10L;
    private static final Long RESTAURANT_ID = 3L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MessagerieServiceImpl(chat, resolver, restaurantRepository, userRepository,
                jwtTokenProvider, messageUnifieRepository);
    }

    private User client() {
        User u = new User();
        u.setId(CLIENT_ID);
        u.setEmail("client@test.mysugu");
        u.setNom("Traore");
        u.setPrenom("Issiaka");
        u.setAvatar("avatar.png");
        u.setRole(UserRole.CLIENT);
        return u;
    }

    private Restaurant restaurant() {
        Restaurant r = new Restaurant();
        r.setId(RESTAURANT_ID);
        r.setNom("Le Bon Resto");
        r.setLogoUrl("logo.png");
        return r;
    }

    private void mockClientAuth() {
        when(jwtTokenProvider.getEmailFromToken("sometoken")).thenReturn("client@test.mysugu");
        when(userRepository.findByEmail("client@test.mysugu")).thenReturn(Optional.of(client()));
    }

    @Test
    void envoyerMessage_delegates_to_conversationService_append_with_correct_participant_refs() {
        mockClientAuth();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant()));

        MessageUnifie saved = MessageUnifie.builder()
                .id(1L).conversationId(99L)
                .expediteurType(CUSTOMER).expediteurId(CLIENT_ID)
                .contenu("hi").attachments(List.of()).seen(false)
                .createdAt(LocalDateTime.now())
                .build();
        when(chat.append(new ParticipantRef(CUSTOMER, CLIENT_ID), new ParticipantRef(RESTAURANT, RESTAURANT_ID),
                "hi", List.of())).thenReturn(saved);
        when(resolver.userInfo(CLIENT_ID)).thenReturn(userInfoMap("Issiaka", "Traore", "avatar.png"));

        MessageChatCreateDTO dto = new MessageChatCreateDTO();
        dto.setRestaurantId(RESTAURANT_ID);
        dto.setContenu("hi");

        MessageChatDTO result = service.envoyerMessage(TOKEN, dto);

        verify(chat).append(new ParticipantRef(CUSTOMER, CLIENT_ID), new ParticipantRef(RESTAURANT, RESTAURANT_ID),
                "hi", List.of());
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getConversationId()).isEqualTo(99L);
        assertThat(result.getContenu()).isEqualTo("hi");
        assertThat(result.getExpediteurId()).isEqualTo(CLIENT_ID);
        assertThat(result.getEnvoyeParMoi()).isTrue();
    }

    @Test
    void getConversations_builds_ConversationDTO_from_conversationsFor_with_unseenCount() {
        mockClientAuth();

        ConversationUnifiee conv = ConversationUnifiee.builder()
                .id(99L)
                .partyAType(CUSTOMER).partyAId(CLIENT_ID)
                .partyBType(RESTAURANT).partyBId(RESTAURANT_ID)
                .commandeId(5L)
                .dernierMessage("salut")
                .dernierMessageAt(LocalDateTime.now())
                .dernierExpediteurType(CUSTOMER).dernierExpediteurId(CLIENT_ID)
                .createdAt(LocalDateTime.now())
                .build();

        ParticipantRef me = new ParticipantRef(CUSTOMER, CLIENT_ID);
        ParticipantRef other = new ParticipantRef(RESTAURANT, RESTAURANT_ID);
        when(chat.conversationsFor(me, RESTAURANT)).thenReturn(List.of(conv));
        when(chat.otherParty(conv, me)).thenReturn(other);
        when(chat.unseenCount(conv, me)).thenReturn(2L);
        when(resolver.userInfo(CLIENT_ID)).thenReturn(userInfoMap("Issiaka", "Traore", "avatar.png"));
        when(resolver.restaurantInfo(RESTAURANT_ID)).thenReturn(restaurantInfoMap("Le Bon Resto", "logo.png"));

        List<ConversationDTO> result = service.getConversations(TOKEN);

        assertThat(result).hasSize(1);
        ConversationDTO dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(99L);
        assertThat(dto.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(dto.getClientNom()).isEqualTo("Traore");
        assertThat(dto.getClientPrenom()).isEqualTo("Issiaka");
        assertThat(dto.getClientAvatar()).isEqualTo("avatar.png");
        assertThat(dto.getRestaurantId()).isEqualTo(RESTAURANT_ID);
        assertThat(dto.getRestaurantNom()).isEqualTo("Le Bon Resto");
        assertThat(dto.getRestaurantLogo()).isEqualTo("logo.png");
        assertThat(dto.getCommandeId()).isEqualTo(5L);
        assertThat(dto.getDernierMessage()).isEqualTo("salut");
        assertThat(dto.getNombreNonLus()).isEqualTo(2L);
    }

    private Map<String, Object> userInfoMap(String fName, String lName, String image) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", CLIENT_ID);
        m.put("f_name", fName);
        m.put("l_name", lName);
        m.put("name", (fName + " " + lName).trim());
        m.put("image", image);
        m.put("phone", "");
        m.put("email", "client@test.mysugu");
        m.put("shops", List.of());
        return m;
    }

    private Map<String, Object> restaurantInfoMap(String nom, String logo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", RESTAURANT_ID);
        m.put("f_name", nom);
        m.put("l_name", "");
        m.put("name", nom);
        m.put("image", logo);
        m.put("shops", List.of(Map.of("name", nom)));
        return m;
    }
}
