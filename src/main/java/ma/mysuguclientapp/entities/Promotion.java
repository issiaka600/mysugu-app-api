package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "promotions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Integer pourcentage; // 10, 20, 30, etc.
    
    @Column(nullable = false)
    private LocalDateTime dateDebut;
    
    @Column(nullable = false)
    private LocalDateTime dateFin;
    
    private String description;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @OneToMany(mappedBy = "promotion")
    private List<Restaurant> restaurants = new ArrayList<>();

    @Column(unique = true, length = 50)
    private String code;

    @Column(name = "montant_min_commande")
    private BigDecimal montantMinCommande;

    @Column(name = "usage_max")
    private Integer usageMax;

    @Column(name = "usage_count")
    private Integer usageCount = 0;

    @Column(name = "est_flash")
    private Boolean estFlash = false;

    /**
     * Règle unique d'activité d'une promotion : activée ET dans sa fenêtre de dates
     * ET n'ayant pas atteint son plafond d'utilisation. Utilisée par le catalogue
     * (feed promo, badge restaurant) et par le calcul de remise commande pour que
     * tous les écrans partagent la même définition d'une promo « en cours ».
     */
    public boolean isActiveNow(LocalDateTime now) {
        if (!Boolean.TRUE.equals(isActive)) {
            return false;
        }
        LocalDateTime t = now != null ? now : LocalDateTime.now();
        if (dateDebut != null && t.isBefore(dateDebut)) {
            return false;
        }
        if (dateFin != null && t.isAfter(dateFin)) {
            return false;
        }
        return usageMax == null || usageCount == null || usageCount < usageMax;
    }
}
