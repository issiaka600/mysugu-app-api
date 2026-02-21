package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class LocationUpdateDTO {
    private Double latitude;
    private Double longitude;
    private String adresse;
    private String ville;
    private String pays;
    private String codePostal;
}