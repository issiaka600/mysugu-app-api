package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * OTP de réinitialisation de mot de passe vendeur (analogue 6valley `password_resets`).
 *
 * Le flux app est : forgot-password (crée un code 4 chiffres) -> verify-otp -> reset-password,
 * keyé par l'identité 6valley (email du vendeur, ou téléphone en repli). On n'utilise pas
 * TokenVerification (dont `token` est unique) car un OTP à 4 chiffres n'est pas unique entre
 * comptes. Expiration 2 minutes comme 6valley. Mêmes conventions que {@link OtpResetLivreur}.
 */
@Entity
@Table(name = "otp_reset_seller",
        indexes = @Index(name = "idx_otp_reset_seller_identity_code", columnList = "identity,code"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OtpResetSeller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String identity;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
