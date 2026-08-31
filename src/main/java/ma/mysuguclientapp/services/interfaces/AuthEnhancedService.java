package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.auth.*;

import java.util.List;

public interface AuthEnhancedService {
    void envoyerEmailVerification(Long userId);
    void renvoyerEmailVerification(String email);
    void verifierEmail(VerifyEmailDTO dto);
    void demanderReinitialisationMotDePasse(ForgotPasswordDTO dto);
    void reinitialiserMotDePasse(ResetPasswordDTO dto);

    // OTP (code à 6 chiffres, alternative mobile au lien email)
    void demanderCodeOtp(ForgotPasswordDTO dto);
    String verifierOtp(VerifyOtpDTO dto);

    // Connexion unifiée e-mail/téléphone (correction PDF "Connexion à son compte") :
    // e-mail+mdp -> code envoyé par e-mail (2e étape verifierCodeConnexion) ;
    // téléphone+mdp -> accès direct (loginResponse déjà rempli dans le résultat).
    LoginStepResultDTO loginParIdentifiant(LoginIdentifiantDTO dto);
    ma.mysuguclientapp.dtos.LoginResponseDTO verifierCodeConnexion(VerifyOtpDTO dto);
    RefreshTokenResponseDTO rafraichirToken(RefreshTokenRequestDTO dto);
    void logout(String accessToken, LogoutDTO dto);
    void changerMotDePasse(Long userId, ChangePasswordDTO dto);

    // Address book
    List<AdresseLivraisonDTO> getMesAdresses(Long userId);
    AdresseLivraisonDTO ajouterAdresse(Long userId, AdresseLivraisonCreateDTO dto);
    AdresseLivraisonDTO modifierAdresse(Long userId, Long adresseId, AdresseLivraisonCreateDTO dto);
    void supprimerAdresse(Long userId, Long adresseId);
    AdresseLivraisonDTO definirAdresseParDefaut(Long userId, Long adresseId);

    // RGPD
    void supprimerCompte(Long userId);
}
