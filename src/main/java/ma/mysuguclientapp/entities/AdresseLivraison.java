package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "adresses_livraison")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AdresseLivraison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String libelle; // ex: "Maison", "Bureau"

    @Column(nullable = false)
    private String adresse;

    private String complement;

    private String ville;

    private String codePostal;

    private Double latitude;

    private Double longitude;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
