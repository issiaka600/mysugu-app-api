package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class UserUpdateDTO {
    private String nom;
    private String prenom;
    private String telephone;
    private LocalisationDTO localisation;
}