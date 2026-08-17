package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.TypeNotification;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destinataire_id", nullable = false)
    private User destinataire;

    @Column(nullable = false)
    private String titre;

    @Column(length = 1000, nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeNotification type;

    @Column(nullable = false)
    private Boolean lue = false;

    @Column(name = "lue_at")
    private LocalDateTime lueAt;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "sender_type")
    private String senderType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
