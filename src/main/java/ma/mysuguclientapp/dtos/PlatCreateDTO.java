package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PlatCreateDTO {
    private String nom;
    private String description;
    private BigDecimal prix;
    private List<String> ingredients;
    private String categoriePlat;
    private String categorieProduit;
    private Long restaurantId;
    private Integer tempsPreparation;
    private Integer quantiteStock;
    private Integer seuilAlerteStock;
    private String availabilityMode;
    private LocalDateTime indisponibleJusqua;
    private Boolean removeImage;
    private Boolean topVente;
}
