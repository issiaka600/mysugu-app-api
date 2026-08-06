package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.ParticipantType;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "messages_unifies",
    indexes = @Index(name="idx_msg_unifie_conv", columnList = "conversation_id,created_at"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageUnifie {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name="conversation_id", nullable=false) private Long conversationId;

    /** Commande qui autorise et contextualise ce message. */
    @Column(name="commande_id") private Long commandeId;

    @Enumerated(EnumType.STRING) @Column(name="expediteur_type", nullable=false, length=20)
    private ParticipantType expediteurType;
    @Column(name="expediteur_id", nullable=false) private Long expediteurId;

    @Column(length=2000) private String contenu;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name="messages_unifies_attachments", joinColumns=@JoinColumn(name="message_id"))
    @Column(name="url", length=1024)
    @Builder.Default private List<String> attachments = new ArrayList<>();

    @Column(name="seen") @Builder.Default private boolean seen = false;

    @CreationTimestamp @Column(name="created_at", updatable=false) private LocalDateTime createdAt;
}
