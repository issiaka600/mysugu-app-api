package ma.mysuguclientapp.dtos.auth;

import lombok.Data;

@Data
public class ResetPasswordDTO {
    private String token;
    private String nouveauMotDePasse;
}
