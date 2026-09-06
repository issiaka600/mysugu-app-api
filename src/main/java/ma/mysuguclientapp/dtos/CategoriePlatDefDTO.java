package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class CategoriePlatDefDTO {
    private Long id;
    private String code;
    private String libelle;
    private Integer ordre;
    private Boolean actif;
    private String icone;
    private Long nombrePlats;
}