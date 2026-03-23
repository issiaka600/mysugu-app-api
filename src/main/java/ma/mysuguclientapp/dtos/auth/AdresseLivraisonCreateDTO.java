package ma.mysuguclientapp.dtos.auth;

import lombok.Data;

@Data
public class AdresseLivraisonCreateDTO {
    private String libelle;
    private String adresse;
    private String complement;
    private String ville;
    private String codePostal;
    private Double latitude;
    private Double longitude;
    private Boolean isDefault;
}
