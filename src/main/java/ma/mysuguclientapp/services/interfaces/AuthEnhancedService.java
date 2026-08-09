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
