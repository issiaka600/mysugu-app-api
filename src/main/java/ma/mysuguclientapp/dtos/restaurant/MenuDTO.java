package ma.mysuguclientapp.dtos.restaurant;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Data
public class MenuDTO {
    private Long id;
    private Long restaurantId;
    private String restaurantNom;
    private String nom;
    private String description;
    private LocalTime heureDebut;
    private LocalTime heureFin;
    private String joursSemaine;
    private Boolean isActive;
    private List<MenuPlatItemDTO> plats;

    @Data
    public static class MenuPlatItemDTO {
        private Long menuPlatId;
        private Long platId;
        private String platNom;
        private BigDecimal prixOriginal;
        private BigDecimal prixSpecial;
        private BigDecimal prixEffectif;
        private Integer ordreAffichage;
    }
}
