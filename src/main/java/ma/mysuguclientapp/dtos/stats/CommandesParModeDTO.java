package ma.mysuguclientapp.dtos.stats;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CommandesParModeDTO {
    private String modeReception;
    private Long nombre;
    private Double pourcentage;
}
