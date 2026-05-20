package ma.mysuguclientapp.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import ma.mysuguclientapp.enumerations.StatutMessageContact;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageContactStatutUpdateDTO {

    @NotNull
    private StatutMessageContact statut;
}
