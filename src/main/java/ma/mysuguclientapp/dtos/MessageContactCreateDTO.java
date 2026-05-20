package ma.mysuguclientapp.dtos;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageContactCreateDTO {

    @NotBlank
    @Size(max = 120)
    private String nom;

    @NotBlank
    @Email
    @Size(max = 180)
    private String email;

    @Size(max = 40)
    private String telephone;

    @NotBlank
    @Size(max = 120)
    private String sujet;

    @NotBlank
    @Size(min = 5, max = 4000)
    private String message;
}
