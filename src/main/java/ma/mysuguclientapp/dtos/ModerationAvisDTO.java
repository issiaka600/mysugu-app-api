package ma.mysuguclientapp.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import ma.mysuguclientapp.enumerations.StatutAvis;

@Data @NoArgsConstructor @AllArgsConstructor
public class ModerationAvisDTO {
    @NotNull
    private StatutAvis statut; // APPROUVE ou REJETE
    private String raisonRejet;
}
