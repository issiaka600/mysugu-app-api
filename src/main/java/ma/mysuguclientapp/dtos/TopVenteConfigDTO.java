package ma.mysuguclientapp.dtos;

import lombok.Data;

/** Configuration globale de la rubrique « Top des ventes » (case « nombre de ventes » du dashboard). */
@Data
public class TopVenteConfigDTO {
    private Integer seuilVentes;
}