package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.ConversationDTO;
import ma.mysuguclientapp.dtos.MessageChatCreateDTO;
import ma.mysuguclientapp.dtos.MessageChatDTO;

import java.util.List;

public interface MessagerieService {

    /** Liste des conversations de l'utilisateur courant (client OU propriétaire de restaurant) */
    List<ConversationDTO> getConversations(String accessToken);

    /** Recherche par mot-clé dans les conversations de l'utilisateur courant (contenu des messages) */
    List<ConversationDTO> rechercherConversations(String accessToken, String motCle);

    /** Messages d'une conversation précise ; marque les messages reçus comme lus */
    List<MessageChatDTO> getMessages(String accessToken, Long conversationId);

    /** Envoie un message ; crée la conversation si besoin (côté client, via restaurantId) */
    MessageChatDTO envoyerMessage(String accessToken, MessageChatCreateDTO dto);
}