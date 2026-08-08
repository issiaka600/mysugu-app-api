package ma.mysuguclientapp.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ma.mysuguclientapp.enumerations.NotificationAckEvent;

import java.time.LocalDateTime;

/** Accusé de réception envoyé par les applications mobiles. */
@Data
public class MobileNotificationAckDTO {
    @NotNull
    private NotificationAckEvent event;

    private Long notificationId;
    private Long orderId;
    private Long deliveryOfferId;

    @Size(max = 512)
    private String firebaseMessageId;

    @Size(max = 512)
    private String deviceToken;

    /** Heure observée par le mobile; le serveur utilise maintenant si absente. */
    private LocalDateTime occurredAt;
}
