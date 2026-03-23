package ma.mysuguclientapp.dtos.auth;

import lombok.Data;

@Data
public class ChangePasswordDTO {
    private String ancienMotDePasse;
    private String nouveauMotDePasse;
}
