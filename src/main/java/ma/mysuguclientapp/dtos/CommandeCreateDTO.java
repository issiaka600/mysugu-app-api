package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.util.List;

@Data
public class CommandeCreateDTO {
    private Long clientId;
    private Long restaurantId;
    private List<LigneCommandeCreateDTO> lignes;
    private LocalisationDTO adresseLivraison;
    private String commentaire;
    private String methodePaiement;
    private String modeReception;
}
