package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OptionChoisieDTO {
    private Long optionItemId;
    private String optionGroupNom;
    private String optionNom;
    private BigDecimal prixSupplement;
}
