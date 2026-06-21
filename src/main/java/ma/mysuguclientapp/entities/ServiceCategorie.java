package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "services_categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCategorie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nom;

    private String tag;

    @Column(length = 1000)
    private String description;

    private String imageTopUrl;

    private String imageBannerUrl;

    private String icon;

    private String type;

    private Integer ordre = 0;

    private Boolean isActive = true;
}
