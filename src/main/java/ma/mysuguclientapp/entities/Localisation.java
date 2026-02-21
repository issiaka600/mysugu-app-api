package ma.mysuguclientapp.entities;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Localisation {
    
    private Double latitude;
    private Double longitude;
    private String adresse;
    private String ville;
    private String codePostal;
    private String pays;
}
