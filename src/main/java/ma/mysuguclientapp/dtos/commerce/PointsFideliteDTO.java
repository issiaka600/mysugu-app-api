package ma.mysuguclientapp.dtos.commerce;

import lombok.Data;
import ma.mysuguclientapp.enumerations.NiveauFidelite;

@Data
public class PointsFideliteDTO {
    private Long id;
    private Long userId;
    private String userNom;
    private String userPrenom;
    private Integer pointsTotal;
    private Integer pointsDisponibles;
    private Integer pointsUtilises;
    private NiveauFidelite niveauFidelite;
    private Integer pointsPourProchainNiveau;
    private String prochainNiveau;
}
