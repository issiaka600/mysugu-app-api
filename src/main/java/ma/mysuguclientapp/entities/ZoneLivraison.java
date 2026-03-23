package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "zones_livraison")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ZoneLivraison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private String nom; // ex: "Centre-ville Marrakech", "Guéliz", "Hivernage"

    @Column(name = "rayon_km", precision = 5, scale = 2)
    private BigDecimal rayonKm; // radius in km

    @Column(name = "frais_livraison", precision = 10, scale = 2)
    private BigDecimal fraisLivraison;

    @Column(name = "montant_min_commande", precision = 10, scale = 2)
    private BigDecimal montantMinCommande;

    @Column(name = "temps_estime_minutes")
    private Integer tempsEstimeMinutes;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Center coordinates (restaurant location or custom)
    @Column(name = "centre_latitude")
    private Double centreLatitude;

    @Column(name = "centre_longitude")
    private Double centreLongitude;
}
