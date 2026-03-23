package ma.mysuguclientapp.services.interfaces;

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
}
