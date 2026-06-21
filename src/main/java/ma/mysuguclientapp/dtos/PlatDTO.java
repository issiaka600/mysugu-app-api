package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PlatDTO {
    private Long id;
    private String nom;
    private String description;
    private BigDecimal prix;
    private String currency = "MAD";
    private String currencySymbol = "DH";
    private String imageObjectName;
    private String imageUrl;
    private List<String> ingredients;
    private String categoriePlat;
    private String categorieProduit;
    private Boolean isAvailable;
    private String availabilityMode;
    private LocalDateTime indisponibleJusqua;
    private Integer tempsPreparation;
    private Long restaurantId;
    private String restaurantNom;
}
