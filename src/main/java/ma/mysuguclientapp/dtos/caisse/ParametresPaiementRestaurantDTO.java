package ma.mysuguclientapp.dtos.caisse;

import lombok.Data;
import ma.mysuguclientapp.enumerations.ModePaiementRestaurant;
import ma.mysuguclientapp.enumerations.ModeVersementRestaurant;

@Data
public class ParametresPaiementRestaurantDTO {
    private Long id;
    private Long restaurantId;
    private String restaurantNom;
    private ModePaiementRestaurant modePaiement;
    private Integer periodicitéJours;
    private ModeVersementRestaurant modeVersement;
    private String rib;
    private String nomBeneficiaire;
}
