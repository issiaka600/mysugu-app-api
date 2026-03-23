package ma.mysuguclientapp.dtos.stats;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class HeurePointe {
    private Integer heure; // 0-23
    private Integer jourSemaine; // 1=Lundi...7=Dimanche
    private Long nombreCommandes;
}
