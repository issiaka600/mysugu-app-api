package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class RegisterDTO {
    private String email;
    private String password;
    private String nom;
    private String prenom;
    private String telephone;
    private String role; // CLIENT, LIVREUR, RESTAURANT_OWNER
}