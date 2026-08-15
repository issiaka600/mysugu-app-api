package ma.mysuguclientapp.dtos.auth;

import lombok.Data;

/**
 * Mise à jour administrative d'un utilisateur.
 *
 * <p>Ne porte volontairement que les coordonnées : ni rôle, ni mot de passe, ni email —
 * l'email sert d'identifiant de connexion. Un champ absent laisse la valeur inchangée,
 * même sémantique que {@code updateRestaurant}.
 */
@Data
public class AdminUserUpdateDTO {
    private String nom;
    private String prenom;
    private String telephone;
}
