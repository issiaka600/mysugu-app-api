package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Message individuel au sein d'une Conversation (module MESSAGERIE / CHAT).
 */
@Entity
@Table(name = "messages_chat",
        indexes = @Index(name = "idx_message_chat_conversation", columnList = "conversation_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediteur_id", nullable = false)
    private User expediteur;

    @Column(length = 2000)
    private String contenu;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "lu")
    @Builder.Default
    private Boolean lu = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}