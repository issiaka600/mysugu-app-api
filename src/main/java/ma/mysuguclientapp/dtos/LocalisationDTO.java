package ma.mysuguclientapp.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalisationDTO {
    private Double latitude;
    private Double longitude;
    private String adresse;
    private String ville;
    private String codePostal;
    private String pays;
}