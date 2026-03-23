package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class CommandeUpdateStatusDTO {
    private String statut;
    private String raisonAnnulation;
}
