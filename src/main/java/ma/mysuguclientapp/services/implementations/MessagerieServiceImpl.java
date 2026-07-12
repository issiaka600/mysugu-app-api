package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.exceptions.UnauthorizedException;
import ma.mysuguclientapp.repositories.MessageUnifieRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.chat.ConversationService;
import ma.mysuguclientapp.services.chat.ParticipantResolver;
import ma.mysuguclientapp.services.interfaces.MessagerieService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static ma.mysuguclientapp.enumerations.ParticipantType.CUSTOMER;
import static ma.mysuguclientapp.enumerations.ParticipantType.RESTAURANT;

/**
 * Module MESSAGERIE / CHAT (acheteur <-> vendeur), rebranché (Task 6) sur le store unifié
 * ({@link ConversationService}) : mêmes routes/DTOs qu'avant (contrat MySuKu inchangé), mais lit
 * et écrit désormais dans la même table que le chat livreur (migration Task 4, façade livreur
 * rebranchée en Task 5) — FCM et « vu » gérés par le service unifié (plus de notification
 * dupliquée ici).
 * <p>
 * L'identité « moi » est résolue depuis le rôle de l'utilisateur authentifié : CLIENT devient un
 * {@link ParticipantType#CUSTOMER}, RESTAURANT_OWNER devient son unique {@link ParticipantType#RESTAURANT}
 * (via {@code findByOwnerId}). Le canal ne connaît que les paires CUSTOMER↔RESTAURANT.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MessagerieServiceImpl implements MessagerieService {

    private final ConversationService chat;
    private final ParticipantResolver resolver;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    /** Lecture seule, pour la recherche par mot-clé (ne doit jamais marquer les messages comme vus). */
    private final MessageUnifieRepository messageUnifieRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationDTO> getConversations(String accessToken) {
        User user = getUserFromToken(accessToken);
        ParticipantRef me = resolveMe(user);
        return chat.conversationsFor(me, counterpartType(me)).stream()
                .map(c -> toConversationDTO(c, me))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationDTO> rechercherConversations(String accessToken, String motCle) {
        User user = getUserFromToken(accessToken);
        ParticipantRef me = resolveMe(user);
        List<ConversationUnifiee> mesConversations = chat.conversationsFor(me, counterpartType(me));

        if (motCle == null || motCle.isBlank() || mesConversations.isEmpty()) {
            return mesConversations.stream().map(c -> toConversationDTO(c, me)).toList();
        }

        String needle = motCle.toLowerCase();
        return mesConversations.stream()
                .filter(c -> messageUnifieRepository.findByConversationIdOrderByCreatedAtAsc(c.getId()).stream()
                        .anyMatch(m -> m.getContenu() != null && m.getContenu().toLowerCase().contains(needle)))
                .map(c -> toConversationDTO(c, me))
                .toList();
    }

    @Override
    public List<MessageChatDTO> getMessages(String accessToken, Long conversationId) {
        User user = getUserFromToken(accessToken);
        ParticipantRef me = resolveMe(user);
        ConversationUnifiee conversation = findMyConversation(me, conversationId);
        ParticipantRef other = chat.otherParty(conversation, me);

        List<MessageUnifie> messages = chat.thread(me, other); // marque les messages reçus comme vus

        return messages.stream().map(m -> toMessageDTO(m, me)).toList();
    }

    @Override
    public MessageChatDTO envoyerMessage(String accessToken, MessageChatCreateDTO dto) {
        User user = getUserFromToken(accessToken);
        ParticipantRef me = resolveMe(user);

        if ((dto.getContenu() == null || dto.getContenu().isBlank()) && dto.getImageUrl() == null) {
            throw new BadRequestException("Le message ne peut pas être vide");
        }

        ParticipantRef other = dto.getConversationId() != null
                ? chat.otherParty(findMyConversation(me, dto.getConversationId()), me)
                : resoudreDestinataireNouvelleConversation(me, dto);

        List<String> attachments = dto.getImageUrl() != null ? List.of(dto.getImageUrl()) : List.of();
        MessageUnifie saved = chat.append(me, other, dto.getContenu(), attachments, dto.getCommandeId());

        return toMessageDTO(saved, me);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** CLIENT -> CUSTOMER(user.id) ; RESTAURANT_OWNER -> RESTAURANT(son restaurant, 1 par owner). */
    private ParticipantRef resolveMe(User user) {
        if (user.getRole() == UserRole.CLIENT) {
            return new ParticipantRef(CUSTOMER, user.getId());
        }
        if (user.getRole() == UserRole.RESTAURANT_OWNER) {
            Restaurant restaurant = restaurantRepository.findByOwnerId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Aucun restaurant associé à cet utilisateur"));
            return new ParticipantRef(RESTAURANT, restaurant.getId());
        }
        throw new UnauthorizedException("Ce rôle n'a pas accès à la messagerie vendeur");
    }

    private ParticipantType counterpartType(ParticipantRef me) {
        return me.type() == CUSTOMER ? RESTAURANT : CUSTOMER;
    }

    private ConversationUnifiee findMyConversation(ParticipantRef me, Long conversationId) {
        return chat.conversationsFor(me, counterpartType(me)).stream()
                .filter(c -> c.getId().equals(conversationId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Conversation non trouvee avec l'ID: " + conversationId));
    }

    private ParticipantRef resoudreDestinataireNouvelleConversation(ParticipantRef me, MessageChatCreateDTO dto) {
        // Seul un client peut démarrer une nouvelle conversation ; le vendeur répond dans un fil existant.
        if (me.type() != CUSTOMER) {
            throw new BadRequestException("Seul un client peut démarrer une nouvelle conversation. " +
                    "Le vendeur doit fournir un conversationId pour répondre.");
        }
        if (dto.getRestaurantId() == null) {
            throw new BadRequestException("restaurantId est requis pour démarrer une nouvelle conversation");
        }
        restaurantRepository.findById(dto.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouve avec l'ID: " + dto.getRestaurantId()));
        return new ParticipantRef(RESTAURANT, dto.getRestaurantId());
    }

    private User getUserFromToken(String bearerToken) {
        String jwt = bearerToken != null && bearerToken.startsWith("Bearer ")
                ? bearerToken.substring(7) : bearerToken;
        String email = jwtTokenProvider.getEmailFromToken(jwt);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouve"));
    }

    private ConversationDTO toConversationDTO(ConversationUnifiee conv, ParticipantRef me) {
        ParticipantRef other = chat.otherParty(conv, me);
        ParticipantRef clientRef = me.type() == CUSTOMER ? me : other;
        ParticipantRef restaurantRef = me.type() == RESTAURANT ? me : other;

        var clientInfo = resolver.userInfo(clientRef.id());
        var restaurantInfo = resolver.restaurantInfo(restaurantRef.id());

        ConversationDTO dto = new ConversationDTO();
        dto.setId(conv.getId());
        dto.setClientId(clientRef.id());
        dto.setClientNom((String) clientInfo.get("l_name"));
        dto.setClientPrenom((String) clientInfo.get("f_name"));
        dto.setClientAvatar((String) clientInfo.get("image"));
        dto.setRestaurantId(restaurantRef.id());
        dto.setRestaurantNom((String) restaurantInfo.get("f_name"));
        dto.setRestaurantLogo((String) restaurantInfo.get("image"));
        dto.setCommandeId(conv.getCommandeId());
        dto.setDernierMessage(conv.getDernierMessage());
        dto.setDernierMessageAt(conv.getDernierMessageAt());
        dto.setCreatedAt(conv.getCreatedAt());
        dto.setNombreNonLus(chat.unseenCount(conv, me));
        return dto;
    }

    private MessageChatDTO toMessageDTO(MessageUnifie m, ParticipantRef me) {
        boolean expediteurEstRestaurant = m.getExpediteurType() == RESTAURANT;
        var senderInfo = expediteurEstRestaurant
                ? resolver.restaurantInfo(m.getExpediteurId())
                : resolver.userInfo(m.getExpediteurId());

        MessageChatDTO dto = new MessageChatDTO();
        dto.setId(m.getId());
        dto.setConversationId(m.getConversationId());
        dto.setExpediteurId(m.getExpediteurId());
        // userInfo: f_name=prenom, l_name=nom. restaurantInfo: f_name=nom (raison sociale), l_name="".
        dto.setExpediteurNom((String) senderInfo.get(expediteurEstRestaurant ? "f_name" : "l_name"));
        dto.setExpediteurPrenom((String) senderInfo.get(expediteurEstRestaurant ? "l_name" : "f_name"));
        dto.setContenu(m.getContenu());
        dto.setImageUrl(m.getAttachments() == null || m.getAttachments().isEmpty() ? null : m.getAttachments().get(0));
        dto.setLu(m.isSeen());
        dto.setEnvoyeParMoi(m.getExpediteurType() == me.type() && java.util.Objects.equals(m.getExpediteurId(), me.id()));
        dto.setCreatedAt(m.getCreatedAt());
        return dto;
    }
}
