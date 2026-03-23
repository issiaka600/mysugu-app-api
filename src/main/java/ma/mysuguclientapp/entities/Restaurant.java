package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "restaurants")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Restaurant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String nom;
    
    @Column(length = 1000)
    private String description;
    
    private String logoUrl;
    
    @Column(name = "appreciation")
    private Double appreciation = 0.0; // Note moyenne (0-5)
    
    @Column(name = "nombre_avis")
    private Integer nombreAvis = 0;
    
    @Column(name = "temps_livraison_moyen")
    private Integer tempsLivraisonMoyen; // en minutes
    
    @Embedded
    private Localisation localisation;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private CategorieRestaurant categorie;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;
    
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;
    
    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Plat> plats = new ArrayList<>();
    
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "auto_close_enabled")
    private Boolean autoCloseEnabled = false;

    @Column(name = "heure_ouverture")
    private LocalTime heureOuverture;

    @Column(name = "heure_fermeture")
    private LocalTime heureFermeture;

    @Column(name = "horaires_ouverture", length = 500)
    private String horairesOuverture; // Format JSON ou texte
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
