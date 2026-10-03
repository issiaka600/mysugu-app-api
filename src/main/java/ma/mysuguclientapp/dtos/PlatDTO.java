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
    /** Commission propre au plat ; les trois champs à null ⇒ celle du restaurant s'applique. */
    private String commissionType;
    private BigDecimal commissionPourcentage;
    private BigDecimal commissionMontantFixe;
    /** Renseigne si le plat porte une commission propre : l'admin peut ainsi proposer de la retirer. */
    private Boolean commissionPropre;
    private Long restaurantId;
    private String restaurantNom;
    private List<OptionGroupDTO> optionGroups;
}
