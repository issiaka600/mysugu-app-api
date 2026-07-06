package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.dtos.ConversationDTO;
import ma.mysuguclientapp.dtos.MessageChatCreateDTO;
import ma.mysuguclientapp.dtos.MessageChatDTO;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.Conversation;
import ma.mysuguclientapp.entities.MessageChat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.TypeNotification;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.exceptions.UnauthorizedException;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.ConversationRepository;
import ma.mysuguclientapp.repositories.MessageChatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.MessagerieService;
import ma.mysuguclientapp.services.interfaces.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MessagerieServiceImpl implements MessagerieService {

    private final ConversationRepository conversationRepository;
    private final MessageChatRepository messageChatRepository;
    private final RestaurantRepository restaurantRepository;
    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationDTO> getConversations(String accessToken) {
        User user = getUserFromToken(accessToken);
        List<Conversation> conversations = getConversationsPourUtilisateur(user);
        return conversations.stream().map(c -> toConversationDTO(c, user.getId())).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationDTO> rechercherConversations(String accessToken, String motCle) {
        User user = getUserFromToken(accessToken);
        List<Conversation> mesConversations = getConversationsPourUtilisateur(user);

        if (motCle == null || motCle.isBlank() || mesConversations.isEmpty()) {
            return mesConversations.stream().map(c -> toConversationDTO(c, user.getId())).collect(Collectors.toList());
        }

        List<Long> mesIds = mesConversations.stream().map(Conversation::getId).toList();
        Map<Long, Conversation> parId = mesConversations.stream()
                .collect(Collectors.toMap(Conversation::getId, c -> c));

        List<MessageChat> messagesTrouves = messageChatRepository
                .findByConversationIdInAndContenuContainingIgnoreCaseOrderByCreatedAtDesc(mesIds, motCle);

        Set<Long> conversationsTrouvees = new LinkedHashSet<>();
        for (MessageChat m : messagesTrouves) {
            conversationsTrouvees.add(m.getConversation().getId());
        }

        return conversationsTrouvees.stream()
                .map(id -> toConversationDTO(parId.get(id), user.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageChatDTO> getMessages(String accessToken, Long conversationId) {
        User user = getUserFromToken(accessToken);
        Conversation conversation = findConversation(conversationId);
        verifierParticipant(conversation, user);

        List<MessageChat> messages = messageChatRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        // Marquer comme lus les messages reçus (pas envoyés par le lecteur courant)
        messages.stream()
                .filter(m -> !m.getExpediteur().getId().equals(user.getId()) && !Boolean.TRUE.equals(m.getLu()))
                .forEach(m -> {
                    m.setLu(true);
                    messageChatRepository.save(m);
                });

        return messages.stream().map(m -> toMessageDTO(m, user.getId())).collect(Collectors.toList());
    }

    @Override
    public MessageChatDTO envoyerMessage(String accessToken, MessageChatCreateDTO dto) {
        User user = getUserFromToken(accessToken);

        if ((dto.getContenu() == null || dto.getContenu().isBlank()) && dto.getImageUrl() == null) {
            throw new BadRequestException("Le message ne peut pas être vide");
        }

        Conversation conversation = dto.getConversationId() != null
                ? resoudreConversationExistante(dto.getConversationId(), user)
                : creerOuRecupererConversation(dto, user);

        MessageChat message = MessageChat.builder()
                .conversation(conversation)
                .expediteur(user)
                .contenu(dto.getContenu())
                .imageUrl(dto.getImageUrl())
                .lu(false)
                .build();
        MessageChat saved = messageChatRepository.save(message);

        conversation.setDernierMessage(dto.getContenu() != null ? dto.getContenu() : "[Image]");
        conversation.setDernierMessageAt(LocalDateTime.now());
        conversation.setDernierExpediteurId(user.getId());
        conversationRepository.save(conversation);

        notifierAutrePartie(conversation, user, dto.getContenu());

        return toMessageDTO(saved, user.getId());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private List<Conversation> getConversationsPourUtilisateur(User user) {
        if (user.getRole() == UserRole.RESTAURANT_OWNER) {
            return conversationRepository.findByRestaurant_Owner_IdOrderByDernierMessageAtDesc(user.getId());
        }
        return conversationRepository.findByClientIdOrderByDernierMessageAtDesc(user.getId());
    }

    private Conversation resoudreConversationExistante(Long conversationId, User user) {
        Conversation conversation = findConversation(conversationId);
        verifierParticipant(conversation, user);
        return conversation;
    }

    private Conversation creerOuRecupererConversation(MessageChatCreateDTO dto, User user) {
        // Seul un client peut démarrer une nouvelle conversation ; le vendeur répond dans un fil existant.
        if (user.getRole() != UserRole.CLIENT) {
            throw new BadRequestException("Seul un client peut démarrer une nouvelle conversation. " +
                    "Le vendeur doit fournir un conversationId pour répondre.");
        }
        if (dto.getRestaurantId() == null) {
            throw new BadRequestException("restaurantId est requis pour démarrer une nouvelle conversation");
        }

        Restaurant restaurant = restaurantRepository.findById(dto.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouve avec l'ID: " + dto.getRestaurantId()));

        return conversationRepository.findByClientIdAndRestaurantId(user.getId(), dto.getRestaurantId())
                .orElseGet(() -> {
                    Conversation.ConversationBuilder builder = Conversation.builder()
                            .client(user)
                            .restaurant(restaurant);
                    if (dto.getCommandeId() != null) {
                        Commande commande = commandeRepository.findById(dto.getCommandeId()).orElse(null);
                        builder.commande(commande);
                    }
                    return conversationRepository.save(builder.build());
                });
    }

    private void verifierParticipant(Conversation conversation, User user) {
        boolean estClient = conversation.getClient().getId().equals(user.getId());
        boolean estProprietaire = conversation.getRestaurant().getOwner() != null
                && conversation.getRestaurant().getOwner().getId().equals(user.getId());
        if (!estClient && !estProprietaire) {
            throw new UnauthorizedException("Vous ne faites pas partie de cette conversation");
        }
    }

    private void notifierAutrePartie(Conversation conversation, User expediteur, String contenu) {
        boolean expediteurEstClient = conversation.getClient().getId().equals(expediteur.getId());
        Long destinataireId = expediteurEstClient
                ? (conversation.getRestaurant().getOwner() != null ? conversation.getRestaurant().getOwner().getId() : null)
                : conversation.getClient().getId();

        if (destinataireId == null) {
            return; // restaurant sans propriétaire assigné : pas de destinataire à notifier
        }

        notificationService.envoyerNotification(
                destinataireId,
                "Nouveau message",
                contenu != null ? contenu : "Vous avez reçu une image",
                TypeNotification.MESSAGE,
                conversation.getId(),
                "CONVERSATION"
        );
    }

    private Conversation findConversation(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation non trouvee avec l'ID: " + id));
    }

    private User getUserFromToken(String bearerToken) {
        String jwt = bearerToken != null && bearerToken.startsWith("Bearer ")
                ? bearerToken.substring(7) : bearerToken;
        String email = jwtTokenProvider.getEmailFromToken(jwt);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouve"));
    }

    private ConversationDTO toConversationDTO(Conversation conv, Long lecteurId) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conv.getId());
        dto.setClientId(conv.getClient().getId());
        dto.setClientNom(conv.getClient().getNom());
        dto.setClientPrenom(conv.getClient().getPrenom());
        dto.setClientAvatar(conv.getClient().getAvatar());
        dto.setRestaurantId(conv.getRestaurant().getId());
        dto.setRestaurantNom(conv.getRestaurant().getNom());
        dto.setRestaurantLogo(conv.getRestaurant().getLogoUrl());
        dto.setCommandeId(conv.getCommande() != null ? conv.getCommande().getId() : null);
        dto.setDernierMessage(conv.getDernierMessage());
        dto.setDernierMessageAt(conv.getDernierMessageAt());
        dto.setCreatedAt(conv.getCreatedAt());
        dto.setNombreNonLus(messageChatRepository.countByConversationIdAndLuFalseAndExpediteurIdNot(conv.getId(), lecteurId));
        return dto;
    }

    private MessageChatDTO toMessageDTO(MessageChat m, Long lecteurId) {
        MessageChatDTO dto = new MessageChatDTO();
        dto.setId(m.getId());
        dto.setConversationId(m.getConversation().getId());
        dto.setExpediteurId(m.getExpediteur().getId());
        dto.setExpediteurNom(m.getExpediteur().getNom());
        dto.setExpediteurPrenom(m.getExpediteur().getPrenom());
        dto.setContenu(m.getContenu());
        dto.setImageUrl(m.getImageUrl());
        dto.setLu(m.getLu());
        dto.setEnvoyeParMoi(m.getExpediteur().getId().equals(lecteurId));
        dto.setCreatedAt(m.getCreatedAt());
        return dto;
    }
}
