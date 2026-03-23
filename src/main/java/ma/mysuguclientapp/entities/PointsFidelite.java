package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.NiveauFidelite;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "points_fidelite", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id"})
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PointsFidelite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "points_total")
    private Integer pointsTotal = 0;

    @Column(name = "points_disponibles")
    private Integer pointsDisponibles = 0;

    @Column(name = "points_utilises")
    private Integer pointsUtilises = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_fidelite")
    private NiveauFidelite niveauFidelite = NiveauFidelite.BRONZE;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
