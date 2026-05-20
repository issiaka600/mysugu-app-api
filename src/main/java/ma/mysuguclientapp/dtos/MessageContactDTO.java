package ma.mysuguclientapp.dtos;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageContactDTO {
    private Long id;
    private String nom;
    private String email;
    private String telephone;
    private String sujet;
    private String message;
    private String statut;
    private String reponse;
    private String reponduPar;
    private LocalDateTime reponduAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
