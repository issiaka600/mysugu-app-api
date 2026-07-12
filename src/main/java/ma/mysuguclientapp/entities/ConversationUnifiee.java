package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import ma.mysuguclientapp.enumerations.ParticipantType;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversations_unifiees",
    uniqueConstraints = @UniqueConstraint(name = "uk_conv_parties",
        columnNames = {"party_a_type","party_a_id","party_b_type","party_b_id"}),
    indexes = {
        @Index(name="idx_conv_party_a", columnList = "party_a_type,party_a_id"),
        @Index(name="idx_conv_party_b", columnList = "party_b_type,party_b_id")
    })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationUnifiee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Enumerated(EnumType.STRING) @Column(name="party_a_type", nullable=false, length=20)
    private ParticipantType partyAType;
    @Column(name="party_a_id", nullable=false) private Long partyAId;

    @Enumerated(EnumType.STRING) @Column(name="party_b_type", nullable=false, length=20)
    private ParticipantType partyBType;
    @Column(name="party_b_id", nullable=false) private Long partyBId;

    @Column(name="commande_id") private Long commandeId;

    @Column(name="dernier_message", length=500) private String dernierMessage;
    @Column(name="dernier_message_at") private LocalDateTime dernierMessageAt;
    @Enumerated(EnumType.STRING) @Column(name="dernier_expediteur_type", length=20)
    private ParticipantType dernierExpediteurType;
    @Column(name="dernier_expediteur_id") private Long dernierExpediteurId;

    @CreationTimestamp @Column(name="created_at", updatable=false) private LocalDateTime createdAt;
}
