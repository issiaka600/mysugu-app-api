package ma.mysuguclientapp.dtos.caisse;

import lombok.Data;
import ma.mysuguclientapp.enumerations.ModeVersementRestaurant;
import ma.mysuguclientapp.enumerations.StatutPaiementRestaurant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaiementRestaurantDTO {
    private Long id;
    private Long restaurantId;
    private String restaurantNom;
    private BigDecimal montantTotal;
    private LocalDateTime periodeDebut;
    private LocalDateTime periodeFin;
    private ModeVersementRestaurant modeVersement;
    private String reference;
    private StatutPaiementRestaurant statut;
    private String effectueParNom;
    private String note;
    private Integer nombreCommandes;
    private LocalDateTime datePaiement;
    private LocalDateTime createdAt;
}
