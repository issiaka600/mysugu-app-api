package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OptionItemDTO {
    private Long id;
    private String nom;
    private BigDecimal prixSupplement;
    private Boolean disponible;
    private Integer ordre;
}
