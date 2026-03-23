package ma.mysuguclientapp.dtos.caisse;

import lombok.Data;
import ma.mysuguclientapp.enumerations.StatutDetteRestaurant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DetteRestaurantDTO {
    private Long id;
    private Long restaurantId;
    private String restaurantNom;
    private Long commandeId;
    private String numeroCommande;
    private BigDecimal montantDu;
    private StatutDetteRestaurant statut;
    private String payeParLivreurNom;
    private LocalDateTime datePaiement;
    private LocalDateTime createdAt;
}
