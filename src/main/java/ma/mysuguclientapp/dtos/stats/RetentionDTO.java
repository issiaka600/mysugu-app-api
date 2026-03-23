package ma.mysuguclientapp.dtos.stats;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RetentionDTO {
    private Long totalClients;
    private Long clientsAvec1Commande;
    private Long clientsAvec2PlusCommandes;
    private Double tauxRetention;
    private Double tauxAbandon;
}
