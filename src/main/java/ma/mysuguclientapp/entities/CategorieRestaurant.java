package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.Vertical;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories_restaurant")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategorieRestaurant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String nom; // subsahariens, marocains, maghrébins, orientaux
    
    private String description;
    
    private String imageUrl;

    private String imageTopUrl;

    private String imageBannerUrl;

    /** Verticale à laquelle cette catégorie d'établissement s'applique. Null = RESTAURANT (données historiques). */
    @Enumerated(EnumType.STRING)
    @Column(name = "vertical")
    private Vertical vertical;

    @OneToMany(mappedBy = "categorie", cascade = CascadeType.ALL)
    private List<Restaurant> restaurants = new ArrayList<>();
}
