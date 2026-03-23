package ma.mysuguclientapp.dtos;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AvisCreateDTO {
    @NotNull
    private Long commandeId;

    @Min(1) @Max(5)
    private Integer noteRestaurant;

    @Min(1) @Max(5)
    private Integer noteLivreur;

    @Size(max = 1000)
    private String commentaire;
}
