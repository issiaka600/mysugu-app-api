package ma.mysuguclientapp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Coffre à double sens pour les mots de passe posés par un administrateur.
 *
 * <p>Un mot de passe n'est normalement jamais relisible : la base n'en garde que le hash
 * BCrypt, et c'est très bien ainsi. Mais un support qui doit rappeler un restaurateur sur
 * téléphone n'a alors aucun moyen de lui confirmer son mot de passe — il ne peut que le
 * réinitialiser, ce qui oblige le restaurant à changer d'identifiant à chaque appel.
 *
 * <p>On réserve donc une copie chiffrée <em>réversible</em> (AES-256-GCM), uniquement pour
 * les comptes dont un administrateur a lui-même choisi le mot de passe. Les comptes créés
 * par leurs utilisateurs (clients, livreurs) ne sont jamais concerned : leur inscription
 * normale ne touche pas ce coffre.
 *
 * <p><strong>Ce que cela coûte :</strong> la clé de chiffrement doit rester hors de la base
 * (variable d'environnement {@code ADMIN_PASSWORD_ENCRYPTION_KEY}). Un attaquant qui
 * obtient à la fois le dump PostgreSQL et cette clé obtient les mots de passe des
 * propriétaires. C'est un arbitrage assumé : il porte sur les comptes que l'administration
 * contrôle déjà, pas sur les comptes clients.
 *
 * <p>Sans clé configurée le coffre est désactivé : {@link #encrypt(String)} renvoie
 * {@code null} et les endpoints de lecture expliquent pourquoi. Aucun secret inventé par
 * défaut — une clé codée en dur serait pire qu'une absence de clé.
 */
@Slf4j
@Component
public class AdminPasswordVault {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAILLE_IV = 12;   // taille recommandée par NIST pour GCM
    private static final int TAILLE_TAG = 128;  // bits d'authentification

    private final SecretKeySpec cle;
    private final SecureRandom random = new SecureRandom();

    public AdminPasswordVault(@Value("${app.admin-password.key:}") String cleBase64) {
        if (cleBase64 == null || cleBase64.isBlank()) {
            this.cle = null;
            log.warn("Coffre des mots de passe administrateur DESACTIVÉ : ADMIN_PASSWORD_ENCRYPTION_KEY absente. "
                    + "Les mots de passe définis par un admin ne pourront pas être relus dans le backoffice.");
            return;
        }
        byte[] octets;
        try {
            octets = Base64.getDecoder().decode(cleBase64.trim());
        } catch (IllegalArgumentException e) {
            this.cle = null;
            log.error("Coffre des mots de passe administrateur DÉSACTIVÉ : "
                    + "ADMIN_PASSWORD_ENCRYPTION_KEY n'est pas du Base64 valide.");
            return;
        }
        if (octets.length != 32) {
            this.cle = null;
            log.error("Coffre des mots de passe administrateur DÉSACTIVÉ : la clé doit faire 32 octets "
                    + "Base64 (256 bits), elle en fait {}.", octets.length);
            return;
        }
        this.cle = new SecretKeySpec(octets, "AES");
        log.info("Coffre des mots de passe administrateur actif.");
    }

    public boolean isEnabled() {
        return cle != null;
    }

    /** Chiffre un mot de passe. Renvoie {@code null} si le coffre est désactivé. */
    public String encrypt(String clair) {
        if (cle == null || clair == null || clair.isEmpty()) return null;
        try {
            byte[] iv = new byte[TAILLE_IV];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, cle, new GCMParameterSpec(TAILLE_TAG, iv));
            byte[] chiffre = cipher.doFinal(clair.getBytes(StandardCharsets.UTF_8));

            byte[] sortie = new byte[iv.length + chiffre.length];
            System.arraycopy(iv, 0, sortie, 0, iv.length);
            System.arraycopy(chiffre, 0, sortie, iv.length, chiffre.length);
            return Base64.getEncoder().encodeToString(sortie);
        } catch (Exception e) {
            // Une panne ici ne doit pas faire échouer la création du compte : le hash BCrypt
            // reste valable, seule la relecture dans le backoffice devient impossible.
            log.error("Chiffrement du mot de passe administrateur impossible", e);
            return null;
        }
    }

    /** Déchiffre un mot de passe. Renvoie {@code null} si absent, illisible, ou coffre désactivé. */
    public String decrypt(String chiffre) {
        if (cle == null || chiffre == null || chiffre.isBlank()) return null;
        try {
            byte[] sortie = Base64.getDecoder().decode(chiffre);
            if (sortie.length <= TAILLE_IV) return null;
            byte[] iv = new byte[TAILLE_IV];
            System.arraycopy(sortie, 0, iv, 0, TAILLE_IV);
            byte[] corps = new byte[sortie.length - TAILLE_IV];
            System.arraycopy(sortie, TAILLE_IV, corps, 0, corps.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, cle, new GCMParameterSpec(TAILLE_TAG, iv));
            return new String(cipher.doFinal(corps), StandardCharsets.UTF_8);
        } catch (Exception e) {
            //clé changée, ou valeur corrompue : on ne devine pas.
            log.error("Déchiffrement du mot de passe administrateur impossible", e);
            return null;
        }
    }
}