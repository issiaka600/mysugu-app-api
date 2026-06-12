package ma.mysuguclientapp.services.implementations;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.DeviceToken;
import ma.mysuguclientapp.repositories.DeviceTokenRepository;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmServiceImpl implements FcmService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    public void sendToUser(Long userId, String title, String body, Map<String, String> data) {
        if (!isFirebaseAvailable()) return;

        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(userId);
        if (tokens.isEmpty()) {
            log.debug("Aucun token FCM actif pour l'utilisateur {}", userId);
            return;
        }

        for (DeviceToken deviceToken : tokens) {
            sendToToken(deviceToken.getToken(), title, body, data);
        }
    }

    @Override
    public void sendToToken(String fcmToken, String title, String body, Map<String, String> data) {
        if (!isFirebaseAvailable()) return;

        try {
            String channelId = data != null && data.containsKey("channelId")
                    ? data.get("channelId")
                    : "mysuku_customer_notifications_v1";
            String sound = data != null && data.containsKey("sound")
                    ? data.get("sound")
                    : "default";

            Message.Builder messageBuilder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(channelId)
                                    .setSound(sound)
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound(sound)
                                    .build())
                            .build());

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            log.info("Notification FCM envoyée avec succès: {}", response);

        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("Token FCM invalide ou non enregistré: {}. Désactivation du token.", fcmToken);
                deviceTokenRepository.deactivateByToken(fcmToken);
            } else {
                log.error("Erreur lors de l'envoi de la notification FCM au token {}: {}", fcmToken, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Erreur inattendue lors de l'envoi FCM: {}", e.getMessage());
        }
    }

    @Override
    public void sendToUsers(List<Long> userIds, String title, String body, Map<String, String> data) {
        if (!isFirebaseAvailable() || userIds == null || userIds.isEmpty()) return;
        userIds.forEach(userId -> sendToUser(userId, title, body, data));
    }

    private boolean isFirebaseAvailable() {
        try {
            return !FirebaseApp.getApps().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
