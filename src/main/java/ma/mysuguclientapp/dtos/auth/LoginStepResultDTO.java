package ma.mysuguclientapp.dtos.auth;

import lombok.Builder;
import lombok.Data;
import ma.mysuguclientapp.dtos.LoginResponseDTO;

/**
 * Résultat de la première étape de connexion (correction PDF "Connexion à son compte") :
 * - identifiant = e-mail : {@code requiresOtp = true}, un code de connexion vient d'être
 *   envoyé par e-mail, {@code loginResponse} est null. Le client doit ensuite appeler
 *   POST /api/auth/login-verify-otp avec ce code pour obtenir le token.
 * - identifiant = numéro de téléphone : {@code requiresOtp = false}, {@code loginResponse}
 *   contient déjà le token — accès direct, comme demandé par le PDF.
 */
@Data
@Builder
public class LoginStepResultDTO {
    private boolean requiresOtp;
    private String email;
    private LoginResponseDTO loginResponse;
}
