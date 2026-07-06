package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.MessageChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageChatRepository extends JpaRepository<MessageChat, Long> {

    /** Fil d'une conversation (ancien d'abord) */
    List<MessageChat> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /** Nombre de messages non lus dans une conversation, non envoyés par ce lecteur */
    long countByConversationIdAndLuFalseAndExpediteurIdNot(Long conversationId, Long lecteurId);

    /** Recherche plein-texte simple dans le contenu des messages d'un ensemble de conversations */
    List<MessageChat> findByConversationIdInAndContenuContainingIgnoreCaseOrderByCreatedAtDesc(
            List<Long> conversationIds, String motCle);
}