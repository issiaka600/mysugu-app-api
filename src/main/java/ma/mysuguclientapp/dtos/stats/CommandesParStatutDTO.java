package ma.mysuguclientapp.dtos.stats;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CommandesParStatutDTO {
    private String statut;
    private Long nombre;
    private Double pourcentage;
}
