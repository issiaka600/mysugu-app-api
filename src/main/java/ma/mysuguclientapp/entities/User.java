package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.UserRole;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private String nom;
    
    @Column(nullable = false)
    private String prenom;
    
    private String telephone;

    // --- Legacy livreur (shim Tiktak) : login par indicatif + téléphone ---
    // L'app 6valley authentifie le livreur avec {country_code, phone, password}.
    // MySugu n'avait que `telephone` (email-only login) → on ajoute l'indicatif
    // pour permettre la résolution (country_code, telephone) -> User LIVREUR.
    @Column(name = "country_code")
    private String countryCode;

    @Column(name = "app_language")
    private String appLanguage;

    // Coordonnées bancaires du livreur (écran "bank-info" de l'app).
    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "branch")
    private String branch;

    @Column(name = "account_no")
    private String accountNo;

    @Column(name = "holder_name")
    private String holderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role; // CLIENT, LIVREUR, ADMIN, RESTAURANT_OWNER
    
    private String avatar;
    
    @Embedded
    private Localisation localisation;

    /** Horodatage de la dernière position reçue, utilisé pour ne proposer une course qu'aux livreurs réellement joignables. */
    @Column(name = "last_location_at")
    private LocalDateTime lastLocationAt;
    
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "consent_rgpd")
    private Boolean consentRgpd = false;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "livreur_disponible")
    private Boolean livreurDisponible = false;

    @Column(name = "apple_sub", unique = true)
    private String appleSub;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
