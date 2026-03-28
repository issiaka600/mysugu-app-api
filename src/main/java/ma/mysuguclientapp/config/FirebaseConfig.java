package ma.mysuguclientapp.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    @PostConstruct
    public void initializeFirebase() {
        if (!firebaseEnabled) {
            log.info("Firebase FCM désactivé (firebase.enabled=false).");
            return;
        }
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase déjà initialisé.");
            return;
        }

        File serviceAccountFile = resolveServiceAccountFile();
        if (serviceAccountFile == null) {
            log.warn("Aucun fichier service-account Firebase trouvé. Notifications push désactivées.");
            return;
        }

        try (InputStream serviceAccount = new FileInputStream(serviceAccountFile)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialisé depuis: {}", serviceAccountFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Échec de l'initialisation Firebase: {}. Notifications push désactivées.", e.getMessage());
        }
    }

    /**
     * Résout le fichier service account en essayant :
     * 1. Le chemin configuré dans firebase.service-account-path
     * 2. Le fichier firebase-service-account.json à la racine du projet (fallback local)
     */
    private File resolveServiceAccountFile() {
        // Tentative 1 : chemin configuré
        if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
            File configured = new File(serviceAccountPath);
            if (configured.exists() && configured.isFile()) {
                return configured;
            }
            log.warn("Fichier service account non trouvé à : {}. Tentative de fallback local.", serviceAccountPath);
        }

        // Tentative 2 : fichier local à la racine du projet
        File local = new File("firebase-service-account.json");
        if (local.exists() && local.isFile()) {
            return local;
        }

        return null;
    }
}
