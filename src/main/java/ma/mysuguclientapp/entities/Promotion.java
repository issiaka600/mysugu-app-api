package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    
    @OneToOne(mappedBy = "promotion")
    private Restaurant restaurant;

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
}
