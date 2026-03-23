package ma.mysuguclientapp.dtos.tracking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class GpsLocationDTO {
    private Long commandeId;
    private Long livreurId;
    private Double latitude;
    private Double longitude;
    private Double vitesse;
    private String statut;
    private LocalDateTime timestamp;
}
