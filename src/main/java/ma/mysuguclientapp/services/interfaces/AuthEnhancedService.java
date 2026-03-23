package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.auth.*;

import java.util.List;

public interface AuthEnhancedService {
    void envoyerEmailVerification(Long userId);
    void verifierEmail(VerifyEmailDTO dto);
    void demanderReinitialisationMotDePasse(ForgotPasswordDTO dto);
    void reinitialiserMotDePasse(ResetPasswordDTO dto);
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
