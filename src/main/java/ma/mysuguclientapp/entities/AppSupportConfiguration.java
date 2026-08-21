package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_support_configurations", uniqueConstraints = @UniqueConstraint(columnNames = "app_key"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AppSupportConfiguration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "app_key", nullable = false, unique = true, length = 32)
    private String appKey;
    private String phone;
    private String whatsapp;
    private String email;
    private String address;
    private String workingHours;
    private String website;
    @Lob
    @Column(name = "emergency_contacts_json")
    private String emergencyContactsJson;
}
