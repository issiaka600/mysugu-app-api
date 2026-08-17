package ma.mysuguclientapp.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelCommandeRequestDTO {
    @NotBlank(message = "La raison d'annulation est obligatoire")
    @Size(max = 1000, message = "La raison d'annulation ne peut pas dépasser 1000 caractères")
    private String reason;
}
