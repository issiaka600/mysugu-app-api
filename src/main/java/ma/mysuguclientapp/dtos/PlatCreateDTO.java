package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PlatCreateDTO {
    private String nom;
    private String description;
    private BigDecimal prix;
    private List<String> ingredients;
    private String categoriePlat;
    private String categorieProduit;
    private Long restaurantId;
    private Integer tempsPreparation;
    private Integer quantiteStock;
    private Integer seuilAlerteStock;
    private String availabilityMode;
    private LocalDateTime indisponibleJusqua;
    private Boolean removeImage;
    private Boolean topVente;

// Commission propre au plat. Contrairement au restaurant, l'admin doit pouvoir *retirer*
    // une commission qu'il avait mise : sans cela il n'y aurait aucun moyen de revenir à la
    // commission du resto. D'où le retrait explicite — « champ absent » reste « ne rien
    // changer », comme pour le restaurant, pour qu'un appelant qui ignore cette feature
    // n'efface pas une commission saisie par erreur.
    /** true = retirer la commission propre du plat et ré-hériter celle du restaurant. */
    private Boolean resetCommission;
    /** POURCENTAGE ou FIXE. */
    private String commissionType;
    private BigDecimal commissionPourcentage;
    private BigDecimal commissionMontantFixe;
}
