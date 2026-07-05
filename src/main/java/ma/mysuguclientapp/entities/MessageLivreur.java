package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Message de chat livreur <-> interlocuteur (client/vendeur/admin). Feature ABSENTE de MySugu,
 * reconstruite pour le shim (contrat 6valley §5.14–§5.17).
 *
 * Une "conversation" est le regroupement (livreur, interlocuteurType, interlocuteurId).
 * NB : le counterpart (app client/vendeur qui répond) n'existe pas encore côté MySugu — voir
 * TIKTAK_LIVREUR_MIGRATION_TECHSPEC.md §9. Le livreur peut envoyer/consulter ; la réception
 * côté client reste à câbler dans l'app client.
 */
@Entity
@Table(name = "messages_livreur",
        indexes = @Index(name = "idx_msg_livreur_conv", columnList = "livreur_id,interlocuteur_type,interlocuteur_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageLivreur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livreur_id", nullable = false)
    private User livreur;

    /** customer | seller | admin */
    @Column(name = "interlocuteur_type", nullable = false, length = 20)
    private String interlocuteurType;

    @Column(name = "interlocuteur_id", nullable = false)
    private Long interlocuteurId;

    @Column(length = 2000)
    private String message;

    @Column(name = "sent_by_delivery_man")
    private Boolean sentByDeliveryMan = true;

    @Column(name = "seen_by_delivery_man")
    private Boolean seenByDeliveryMan = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "messages_livreur_attachments", joinColumns = @JoinColumn(name = "message_id"))
    @Column(name = "url", length = 1024)
    @Builder.Default
    private List<String> attachments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
