package ma.mysuguclientapp.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DisponibiliteDTO {

    @NotNull
    private Boolean disponible;
}
