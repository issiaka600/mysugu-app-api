package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.CategoriePlat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Plat {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String nom;
    
    @Column(length = 1000)
    private String description;
    
    @Column(nullable = false)
    private BigDecimal prix;
    
    private String imageUrl;
    
    @ElementCollection
    @CollectionTable(name = "plat_ingredients", joinColumns = @JoinColumn(name = "plat_id"))
    @Column(name = "ingredient")
    private List<String> ingredients = new ArrayList<>();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;
    
    @Column(name = "is_available")
    private Boolean isAvailable = true;
    
    @Column(name = "temps_preparation")
    private Integer tempsPreparation; // en minutes
    
    @Enumerated(EnumType.STRING)
    private CategoriePlat categoriePlat; // ENTREE, PLAT_PRINCIPAL, DESSERT, BOISSON
}
