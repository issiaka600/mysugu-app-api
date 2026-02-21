package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PlatCreateDTO {
    private String nom;
    private String description;
    private BigDecimal prix;
    private List<String> ingredients;
    private String categoriePlat;
    private Long restaurantId;
    private Integer tempsPreparation;
}