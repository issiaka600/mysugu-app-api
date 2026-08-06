package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.CampagneNotificationRequestDTO;
import ma.mysuguclientapp.dtos.CampagneNotificationResultDTO;
import ma.mysuguclientapp.dtos.NotificationDTO;
import ma.mysuguclientapp.enumerations.TypeNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationService {
    Page<NotificationDTO> getMesNotifications(String accessToken, Pageable pageable);
    List<NotificationDTO> getMesNotificationsNonLues(String accessToken);
    long getNombreNonLues(String accessToken);
    NotificationDTO marquerCommeLue(String accessToken, Long notificationId);
    void marquerToutesCommeLues(String accessToken);

    void envoyerNotification(Long userId, String titre, String message, TypeNotification type, Long entityId, String entityType);
    void envoyerNotificationCommande(Long userId, String numeroCommande, TypeNotification type, Long commandeId);
    void envoyerNotificationSysteme(Long userId, String titre, String message);

    /** Push visible de messagerie, avec contexte de navigation et badge non-lu réel. */
    void envoyerNotificationMessage(Long destinataireUserId, String senderId, String senderType,
                                    String senderName, Long conversationId, Long commandeId, long unreadCount);

    /** Envoie une campagne de notification (in-app + push) à un segment d'utilisateurs. */
    CampagneNotificationResultDTO envoyerCampagne(CampagneNotificationRequestDTO request);

    /** Retourne toutes les notifications envoyées pour une entité (ex. une promotion). */
    List<NotificationDTO> getNotificationsParEntite(Long entityId, String entityType);
}
