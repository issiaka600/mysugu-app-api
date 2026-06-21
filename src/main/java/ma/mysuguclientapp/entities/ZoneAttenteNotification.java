package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "zones_attente_notifications")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ZoneAttenteNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String telephone;
    private Double latitude;
    private Double longitude;
    private String ville;
    private String pays;

    @Column(nullable = false)
    private Boolean notifie = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
