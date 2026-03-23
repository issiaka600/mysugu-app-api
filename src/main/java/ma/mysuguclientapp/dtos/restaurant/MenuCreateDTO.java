package ma.mysuguclientapp.dtos.restaurant;

import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
public class MenuCreateDTO {
    private Long restaurantId;
    private String nom;
    private String description;
    private LocalTime heureDebut;
    private LocalTime heureFin;
    private String joursSemaine;
    private List<MenuPlatDTO> plats;

    @Data
    public static class MenuPlatDTO {
        private Long platId;
        private java.math.BigDecimal prixSpecial;
        private Integer ordreAffichage;
    }
}
