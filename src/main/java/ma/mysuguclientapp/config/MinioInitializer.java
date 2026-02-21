package ma.mysuguclientapp.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.services.implementations.MinioService;
import org.springframework.stereotype.Component;

/**
 * Initialise-les buckets MinIO au démarrage de l'application
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MinioInitializer {

    private final MinioService minioService;

    @PostConstruct
    public void init() {
        log.info("Initialisation des buckets MinIO...");
        try {
            minioService.initBuckets();
            log.info("Buckets MinIO initialisés avec succès");
        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation des buckets MinIO", e);
        }
    }
}
