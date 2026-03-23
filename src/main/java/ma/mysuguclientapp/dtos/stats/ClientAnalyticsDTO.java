package ma.mysuguclientapp.dtos.stats;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ClientAnalyticsDTO {
    private Long clientId;
    private String nom;
    private String prenom;
    private String email;
    private Long nombreCommandes;
    private BigDecimal totalDepense;
    private LocalDateTime premiereCommande;
    private LocalDateTime derniereCommande;
    private Long joursDepuisDerniereCommande;
}
