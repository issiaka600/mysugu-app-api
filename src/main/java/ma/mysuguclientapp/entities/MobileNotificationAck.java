package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.NotificationAckEvent;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Journal des accusés de réception envoyés par une application mobile. */
@Entity
@Table(name = "mobile_notification_acks",
        indexes = {
                @Index(name = "idx_mobile_ack_user_created", columnList = "user_id,created_at"),
                @Index(name = "idx_mobile_ack_order", columnList = "order_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MobileNotificationAck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationAckEvent event;

    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "delivery_offer_id")
    private Long deliveryOfferId;

    @Column(name = "firebase_message_id", length = 512)
    private String firebaseMessageId;

    @Column(name = "device_token", length = 512)
    private String deviceToken;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
