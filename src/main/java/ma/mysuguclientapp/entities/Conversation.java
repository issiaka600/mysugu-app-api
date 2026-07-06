package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Conversation entre un client et un restaurant (module MESSAGERIE / CHAT, ticket T9
 * du techspec migration TikTak livreur — "counterpart" client/vendeur manquant).
 * Un seul fil par couple (client, restaurant) ; le contenu des messages est dans MessageChat.
 */
@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(name = "uk_conversation_client_restaurant", columnNames = {"client_id", "restaurant_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    /** Contexte optionnel : la commande à l'origine de la conversation, si applicable */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id")
    private Commande commande;

    @Column(name = "dernier_message", length = 500)
    private String dernierMessage;

    @Column(name = "dernier_message_at")
    private LocalDateTime dernierMessageAt;

    @Column(name = "dernier_expediteur_id")
    private Long dernierExpediteurId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
