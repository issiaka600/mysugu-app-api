package ma.mysuguclientapp.dtos.auth;

import lombok.Data;

/**
 * Définition administrative du mot de passe d'un propriétaire.
 *
 * <p>Deux modes, un seul champ à remplir par l'administrateur :
 * <ul>
 *   <li>{@code generer = true} — le serveur tire un mot de passe lisible et le renvoie
 *       en clair dans la réponse, pour que le backoffice puisse l'afficher et le copier
 *       une seule fois. Rien n'est stocké en clair côté base.</li>
 *   <li>{@code motDePasse} non vide — l'administrateur choisit lui-même la valeur.</li>
 * </ul>
 *
 * <p>Si les deux sont absents, la requête est rejetée : on ne peut pas "effacer" un mot
 * de passe par ce endpoint.
 */
@Data
public class AdminPasswordSetDTO {
    private String motDePasse;
    private Boolean generer;
}