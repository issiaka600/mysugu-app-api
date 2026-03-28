package ma.mysuguclientapp.services.interfaces;

public interface DeviceTokenService {

    /**
     * Enregistre ou met à jour un token FCM pour un utilisateur.
     */
    void registerToken(Long userId, String token, String platform);

    /**
     * Désactive un token FCM (ex: déconnexion).
     */
    void deactivateToken(String token);

    /**
     * Désactive tous les tokens d'un utilisateur (ex: déconnexion sur tous les appareils).
     */
    void deactivateAllTokensForUser(Long userId);
}
