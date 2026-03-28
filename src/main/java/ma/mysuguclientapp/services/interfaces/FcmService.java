package ma.mysuguclientapp.services.interfaces;

import java.util.List;
import java.util.Map;

public interface FcmService {

    /**
     * Envoie une notification push à tous les appareils actifs d'un utilisateur.
     */
    void sendToUser(Long userId, String title, String body, Map<String, String> data);

    /**
     * Envoie une notification push à un token FCM spécifique.
     */
    void sendToToken(String fcmToken, String title, String body, Map<String, String> data);

    /**
     * Envoie une notification push à plusieurs utilisateurs.
     */
    void sendToUsers(List<Long> userIds, String title, String body, Map<String, String> data);
}
