package ma.mysuguclientapp.services.implementations;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.DeviceToken;
import ma.mysuguclientapp.repositories.DeviceTokenRepository;
import ma.mysuguclientapp.services.FcmDeliveryResult;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmServiceImpl implements FcmService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    public void sendToUser(Long userId, String title, String body, Map<String, String> data) {
        sendToUserWithResult(userId, title, body, data);
    }

    @Override
    public FcmDeliveryResult sendToUserWithResult(Long userId, String title, String body, Map<String, String> data) {
        if (!isFirebaseAvailable()) {
            return new FcmDeliveryResult(0, 0, List.of(), List.of("Firebase indisponible"));
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(userId);
        if (tokens.isEmpty()) {
            log.debug("Aucun token FCM actif pour l'utilisateur {}", userId);
            return new FcmDeliveryResult(0, 0, List.of(), List.of("Aucun token FCM actif"));
        }

        List<String> messageIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (DeviceToken deviceToken : tokens) {
            log.info("Envoi FCM utilisateur={} tokenId={} plateforme={} token={}", userId,
                    deviceToken.getId(), deviceToken.getPlatform(), maskToken(deviceToken.getToken()));
            FcmDeliveryResult result = sendToTokenWithResult(deviceToken.getToken(), title, body, data);
            messageIds.addAll(result.firebaseMessageIds());
            errors.addAll(result.errors());
        }
        return new FcmDeliveryResult(tokens.size(), messageIds.size(), messageIds, errors);
    }

    @Override
    public void sendToToken(String fcmToken, String title, String body, Map<String, String> data) {
        sendToTokenWithResult(fcmToken, title, body, data);
    }

    private FcmDeliveryResult sendToTokenWithResult(String fcmToken, String title, String body, Map<String, String> data) {
        if (!isFirebaseAvailable()) {
            return new FcmDeliveryResult(1, 0, List.of(), List.of("Firebase indisponible"));
        }

        try {
            String channelId = data != null && data.containsKey("channelId")
                    ? data.get("channelId")
                    : "mysuku_customer_notifications_v1";
            String sound = data != null && data.containsKey("sound")
                    ? data.get("sound")
                    : "default";
            String androidSound = data != null && data.containsKey("androidSound")
                    ? data.get("androidSound") : sound;
            String apnsSound = data != null && data.containsKey("apnsSound")
                    ? data.get("apnsSound") : sound;

            AndroidNotification.Builder androidNotification = AndroidNotification.builder()
                    .setChannelId(channelId)
                    .setSound(androidSound);
            if (data != null && "public".equalsIgnoreCase(data.get("androidVisibility"))) {
                androidNotification.setVisibility(AndroidNotification.Visibility.PUBLIC);
            }
            if (data != null && data.containsKey("notificationTag")) {
                androidNotification.setTag(data.get("notificationTag"));
            }

            AndroidConfig.Builder androidConfig = AndroidConfig.builder()
                    .setNotification(androidNotification.build());
            if (data != null && "high".equalsIgnoreCase(data.get("priority"))) {
                androidConfig.setPriority(AndroidConfig.Priority.HIGH);
            }
            if (data != null && data.containsKey("collapseKey")) {
                androidConfig.setCollapseKey(data.get("collapseKey"));
            }
            if (data != null && data.containsKey("ttlSeconds")) {
                try {
                    androidConfig.setTtl(Math.max(1L, Long.parseLong(data.get("ttlSeconds"))) * 1000L);
                } catch (NumberFormatException ignored) {
                    log.warn("TTL FCM Android invalide: {}", data.get("ttlSeconds"));
                }
            }

            int badge = 1;
            if (data != null && data.containsKey("badge")) {
                try {
                    badge = Math.max(0, Integer.parseInt(data.get("badge")));
                } catch (NumberFormatException ignored) {
                    log.warn("Badge FCM iOS invalide: {}", data.get("badge"));
                }
            }
            Aps.Builder aps = Aps.builder()
                    .setAlert(ApsAlert.builder().setTitle(title).setBody(body).build())
                    .setSound(apnsSound)
                    .setBadge(badge)
                    .setContentAvailable(true);
            if (data != null && data.containsKey("apnsInterruptionLevel")) {
                aps.putCustomData("interruption-level", data.get("apnsInterruptionLevel"));
            }
            if (data != null && data.containsKey("apnsThreadId")) {
                aps.setThreadId(data.get("apnsThreadId"));
            }
            ApnsConfig.Builder apnsConfig = ApnsConfig.builder().setAps(aps.build());
            if (data != null && data.containsKey("apnsPushType")) {
                apnsConfig.putHeader("apns-push-type", data.get("apnsPushType"));
            }
            if (data != null && data.containsKey("apnsPriority")) {
                apnsConfig.putHeader("apns-priority", data.get("apnsPriority"));
            }

            Message.Builder messageBuilder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(androidConfig.build())
                    .setApnsConfig(apnsConfig.build());

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            Message message = messageBuilder.build();
            log.info("Payload FCM token={} title={} body={} data={} androidChannel={} " +
                            "androidSound={} androidPriority={} apnsSound={} apnsPushType={} apnsPriority={}",
                    maskToken(fcmToken), title, body, data, channelId, androidSound,
                    data != null ? data.get("priority") : null, apnsSound,
                    data != null ? data.get("apnsPushType") : null,
                    data != null ? data.get("apnsPriority") : null);
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Notification FCM envoyée token={} messageId={}", maskToken(fcmToken), response);
            return new FcmDeliveryResult(1, 1, List.of(response), List.of());

        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("Échec FCM token={} code={} errorCode={} message={}; token désactivé",
                        maskToken(fcmToken), e.getMessagingErrorCode(),
                        e.getErrorCode(), e.getMessage(), e);
                deviceTokenRepository.deactivateByToken(fcmToken);
            } else {
                log.error("Échec FCM token={} code={} errorCode={} message={}", maskToken(fcmToken),
                        e.getMessagingErrorCode(), e.getErrorCode(), e.getMessage(), e);
            }
            return new FcmDeliveryResult(1, 0, List.of(), List.of(
                    "messagingCode=" + e.getMessagingErrorCode()
                            + ", errorCode=" + e.getErrorCode()
                            + ", message=" + e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur inattendue lors de l'envoi FCM: {}", e.getMessage());
            return new FcmDeliveryResult(1, 0, List.of(), List.of(String.valueOf(e.getMessage())));
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

    private String maskToken(String token) {
        if (token == null || token.length() < 12) return "***";
        return token.substring(0, 6) + "..." + token.substring(token.length() - 6);
    }
}
