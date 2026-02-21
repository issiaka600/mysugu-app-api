package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PlatDTO {
    private Long id;
    private String nom;
    private String description;
    private BigDecimal prix;
    private String imageUrl;
    private List<String> ingredients;
    private String categoriePlat;
    private Boolean isAvailable;
    private Integer tempsPreparation;
    private Long restaurantId;
    private String restaurantNom;
}