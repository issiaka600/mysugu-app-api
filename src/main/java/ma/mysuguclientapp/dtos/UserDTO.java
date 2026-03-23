package ma.mysuguclientapp.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserDTO {
    private Long id;
    private String email;
    private String nom;
    private String prenom;
    private String telephone;
    private String role;
    private String avatar;
    private LocalisationDTO localisation;
    private Boolean isActive;
    private Boolean livreurDisponible;
    private LocalDateTime createdAt;
}