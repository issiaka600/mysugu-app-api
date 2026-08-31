package ma.mysuguclientapp.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Requête de connexion unifiée (correction PDF "Connexion à son compte") : un seul champ
 * identifiant, e-mail ou numéro de téléphone selon ce que le client saisit — détecté côté
 * serveur (présence d'un "@").
 */
@Data
public class LoginIdentifiantDTO {
    @NotBlank(message = "L'identifiant est obligatoire")
    private String identifiant;
    @NotBlank(message = "Le mot de passe est obligatoire")
    private String password;
}
