package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * OTP de réinitialisation de mot de passe livreur (analogue 6valley `password_resets`).
 *
 * Le flux app est : forgot-password (crée un code 4 chiffres) -> verify-otp -> reset-password.
 * On n'utilise pas TokenVerification (dont `token` est unique) car un OTP à 4 chiffres n'est
 * pas unique entre livreurs. Expiration 2 minutes comme 6valley.
 */
@Entity
@Table(name = "otp_reset_livreur",
        indexes = @Index(name = "idx_otp_reset_tel_code", columnList = "telephone,code"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OtpResetLivreur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String telephone;

    @Column(name = "country_code")
    private String countryCode;

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
