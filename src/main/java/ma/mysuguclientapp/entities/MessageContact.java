package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.StatutMessageContact;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_contact", indexes = {
        @Index(name = "idx_message_contact_statut",     columnList = "statut"),
        @Index(name = "idx_message_contact_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(length = 40)
    private String telephone;

    @Column(nullable = false, length = 120)
    private String sujet;

    @Column(nullable = false, length = 4000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutMessageContact statut;

    @Column(name = "reponse", length = 4000)
    private String reponse;

    @Column(name = "repondu_par", length = 180)
    private String reponduPar;

    @Column(name = "repondu_at")
    private LocalDateTime reponduAt;

    @Column(name = "ip_origin", length = 60)
    private String ipOrigin;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
