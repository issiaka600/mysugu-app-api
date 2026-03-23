package ma.mysuguclientapp.dtos.stats;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CommandeEnCoursDTO {
    private Long commandeId;
    private String numeroCommande;
    private String statut;
    private String restaurantNom;
    private String clientNom;
    private Long minutesDepuisCreation;
    private Boolean enRetard;
}
