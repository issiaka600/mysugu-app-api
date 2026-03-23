package ma.mysuguclientapp.dtos.auth;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdresseLivraisonDTO {
    private Long id;
    private String libelle;
    private String adresse;
    private String complement;
    private String ville;
    private String codePostal;
    private Double latitude;
    private Double longitude;
    private Boolean isDefault;
    private LocalDateTime createdAt;
}
