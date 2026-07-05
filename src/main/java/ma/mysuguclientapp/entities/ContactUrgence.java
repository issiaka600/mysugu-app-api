package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Contact d'urgence proposé au livreur (analogue 6valley `emergency_contacts`).
 *
 * En 6valley ces contacts sont rattachés au vendeur (seller_id). Ici on les rattache
 * au restaurant (nullable = contact global plateforme). Exposé par
 * GET /api/v2/delivery-man/emergency-contact-list. Feature absente de MySugu (techspec §9).
 */
@Entity
@Table(name = "contacts_urgence")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ContactUrgence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null = contact global plateforme ; sinon rattaché à un restaurant. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Column(nullable = false)
    private String nom;

    @Column(name = "telephone", nullable = false)
    private String telephone;

    @Column(name = "actif")
    private Boolean actif = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
