package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class AppleAuthRequestDTO {
    private String idToken;
    private String nonce;
    private String role;
    private String telephone;
    private String email;
    private String prenom;
    private String nom;
}
