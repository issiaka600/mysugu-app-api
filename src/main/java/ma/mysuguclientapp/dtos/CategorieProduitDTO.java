package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class CategorieProduitDTO {
    private Long id;
    private String vertical;
    private String code;
    private String libelle;
    private Integer ordre;
    private Boolean actif;
}
