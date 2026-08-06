package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Trace technique d'un envoi FCM déclenché par une notification in-app. */
@Entity
@Table(name = "tentatives_notification_fcm")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TentativeNotificationFcm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Column(name = "tokens_attempted", nullable = false)
    private Integer tokensAttempted;

    @Column(name = "tokens_sent", nullable = false)
    private Integer tokensSent;

    @Column(name = "firebase_message_ids", length = 4000)
    private String firebaseMessageIds;

    @Column(name = "errors", length = 4000)
    private String errors;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
