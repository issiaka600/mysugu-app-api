package ma.mysuguclientapp.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageContactReponseDTO {

    @NotBlank
    @Size(min = 5, max = 4000)
    private String reponse;

    /**
     * Si vrai, envoie la reponse par email au demandeur.
     */
    private boolean envoyerEmail = true;
}
