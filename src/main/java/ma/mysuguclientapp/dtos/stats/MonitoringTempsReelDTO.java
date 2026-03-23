package ma.mysuguclientapp.dtos.stats;

import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MonitoringTempsReelDTO {
    private List<CommandeEnCoursDTO> commandesEnCours;
    private List<AlerteDTO> alertes;
    private Long totalCommandesEnCours;
}
