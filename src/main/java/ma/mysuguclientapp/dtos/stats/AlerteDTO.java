package ma.mysuguclientapp.dtos.stats;

import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AlerteDTO {
    private String type;
    private String message;
    private Long entityId;
    private String entityNom;
    private LocalDateTime detectedAt;
}
