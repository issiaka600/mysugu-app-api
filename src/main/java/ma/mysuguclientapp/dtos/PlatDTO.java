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
    /** Libellé de la catégorie (depuis categorie_plat_defs) pour l'affichage client sans mapping en dur. */
    private String categoriePlatLabel;
    /** Ordre d'affichage de la catégorie, configuré depuis le dashboard. */
    private Integer categoriePlatOrdre;
    /** Emoji/icône de la catégorie, optionnel. */
    private String categoriePlatIcone;
    private String categorieProduit;
    private Boolean isAvailable;
    private String availabilityMode;
    private LocalDateTime indisponibleJusqua;
    private Integer tempsPreparation;
    private Integer quantiteStock;
    private Boolean stockGere;
    private Boolean alerteStockBas;
    private Boolean topVente;
    private Long restaurantId;
    private String restaurantNom;
    private List<OptionGroupDTO> optionGroups;
}
