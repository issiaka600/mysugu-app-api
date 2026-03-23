package ma.mysuguclientapp.dtos;

import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AvisDTO {
    private Long id;
    private Long commandeId;
    private Long auteurId;
    private String auteurNom;
    private String auteurPrenom;
    private String auteurAvatar;
    private Long restaurantId;
    private String restaurantNom;
    private Long livreurId;
    private String livreurNom;
    private Integer noteRestaurant;
    private Integer noteLivreur;
    private String commentaire;
    private String statut;
    private String raisonRejet;
    private LocalDateTime createdAt;
}
