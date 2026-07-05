package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Photo de preuve de livraison (analogue 6valley `order_delivery_verifications`).
 * Uploadée par le livreur via POST /api/v2/delivery-man/order-delivery-verification.
 * L'URL pointe vers un objet MinIO (bucket mysugu). 0..n par commande.
 */
@Entity
@Table(name = "preuves_livraison")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PreuveLivraison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @Column(name = "image_url", nullable = false, length = 1024)
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
