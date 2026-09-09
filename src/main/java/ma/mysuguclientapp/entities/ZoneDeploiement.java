package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Zone géographique où le service MySugu est déployé (ex : "Marrakech").
 * L'admin définit ces zones ; chaque restaurant y est rattaché.
 * À la création d'une commande en livraison, l'adresse du client est
 * vérifiée contre la zone du restaurant avant tout traitement.
 */
@Entity
@Table(name = "zones_deploiement")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ZoneDeploiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nom lisible de la zone (ex : "Marrakech", "Guéliz"). */
    @Column(nullable = false, unique = true)
    private String nom;

    @Column(length = 500)
    private String description;

    /** Latitude du centre géographique de la zone. */
    @Column(name = "centre_latitude")
    private Double centreLatitude;

    /** Longitude du centre géographique de la zone. */
    @Column(name = "centre_longitude")
    private Double centreLongitude;

    /** Rayon de couverture en kilomètres à partir du centre. */
    @Column(name = "rayon_km", precision = 6, scale = 2)
    private BigDecimal rayonKm;

    /**
     * Frais de livraison minimaux appliqués dans cette zone.
     * S'appliquent pour toute distance ≤ distanceMinKm.
     */
    @Column(name = "frais_livraison_min", precision = 10, scale = 2)
    private BigDecimal fraisLivraisonMin;

    /**
     * Distance en km en dessous de laquelle le frais minimum s'applique.
     * Au-delà : fraisMin + (distance - distanceMin) * prixExtraParKm.
     */
    @Column(name = "distance_min_km", precision = 6, scale = 2)
    private BigDecimal distanceMinKm;

    /**
     * Prix unitaire en DH par km supplémentaire au-delà de distanceMinKm.
     */
    @Column(name = "prix_extra_par_km", precision = 8, scale = 2)
    private BigDecimal prixExtraParKm;

    /**
     * Prix de 1 km en DH pour le modèle de facturation « au kilomètre ».
     * Si renseigné, il prend le pas sur la grille distanceMinKm/prixExtraParKm :
     * frais = max(fraisLivraisonMin, distance × prixParKm).
     * Non renseigné = la zone garde l'ancien modèle (fraisMin + extra/km).
     */
    @Column(name = "prix_par_km", precision = 8, scale = 2)
    private BigDecimal prixParKm;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
