package ma.mysuguclientapp.dtos.stats;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CommandesParPaiementDTO {
    private String methodePaiement;
    private Long nombre;
    private Double pourcentage;
}
