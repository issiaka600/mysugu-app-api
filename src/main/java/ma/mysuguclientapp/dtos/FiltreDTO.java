package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class FiltreDTO {
    private Long id;
    private String contexte;       // RESTAURANT | ALIMENTAIRE | COSMETIQUE
    private String comportement;   // TOUS | PROMOTIONS | ...
    private Long categorieId;
    private String libelle;
    private String icone;
    private Integer ordre;
    private Boolean actif;
}
