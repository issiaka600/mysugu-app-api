package ma.mysuguclientapp.services.implementations;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.FileMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.image-bucket}")
    private String imageBucket;

    /**
     * Initialiser les buckets nécessaires
     */
    public void initBuckets() {
        try {
            createBucketIfNotExists(bucketName);
            createBucketIfNotExists(imageBucket);

            // Créer des buckets séparés pour une meilleure organisation
            createBucketIfNotExists("restaurants");
            createBucketIfNotExists("plats");
            createBucketIfNotExists("avatars");
            createBucketIfNotExists("categories");

            log.info("Tous les buckets MinIO ont été initialisés avec succès");
        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation des buckets MinIO", e);
        }
    }

    /**
     * Créer un bucket s'il n'existe pas
     */
    private void createBucketIfNotExists(String bucket) throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucket)
                .build());

        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucket)
                    .build());

            // Rendre le bucket public en lecture (optionnel)
            // makeBucketPublic(bucket);

            log.info("Bucket créé: {}", bucket);
        } else {
            log.debug("Bucket {} existe déjà", bucket);
        }
    }

    /**
     * Upload un fichier dans MinIO
     */
    public String uploadFile(MultipartFile file, String folder) throws Exception {
        String fileName = generateFileName(file.getOriginalFilename());
        String objectName = folder + "/" + fileName;

        // Déterminer le bucket approprié
        String targetBucket = determineBucket(folder);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(targetBucket)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("Fichier uploadé: {} dans le bucket {}", objectName, targetBucket);
            return objectName;
        }
    }

    /**
     * Obtenir l'URL de téléchargement d'un fichier (URL pré-signée)
     */
    public String getFileUrl(String objectName) throws Exception {
        String bucket = determineBucket(objectName);

        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(objectName)
                        .expiry(7, TimeUnit.DAYS)
                        .build()
        );
    }

    /**
     * Supprimer un fichier
     */
    public void deleteFile(String objectName) throws Exception {
        String bucket = determineBucket(objectName);

        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build()
        );
        log.info("Fichier supprimé: {} du bucket {}", objectName, bucket);
    }

    /**
     * Télécharger un fichier
     */
    public InputStream downloadFile(String objectName) throws Exception {
        String bucket = determineBucket(objectName);

        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build()
        );
    }

    /**
     * Vérifier si un fichier existe
     */
    public boolean fileExists(String objectName) {
        try {
            String bucket = determineBucket(objectName);

            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtenir les métadonnées d'un fichier
     */
    public FileMetadata getFileMetadata(String objectName) throws Exception {
        String bucket = determineBucket(objectName);

        var stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build()
        );

        FileMetadata metadata = new FileMetadata();
        metadata.setObjectName(objectName);
        metadata.setBucket(bucket);
        metadata.setSize(stat.size());
        metadata.setContentType(stat.contentType());
        metadata.setLastModified(stat.lastModified().toLocalDateTime());

        return metadata;
    }

    /**
     * Générer un nom de fichier unique
     */
    private String generateFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }

    /**
     * Déterminer le bucket approprié basé sur le dossier
     */
    private String determineBucket(String path) {
        if (path == null) {
            return imageBucket;
        }

        String lowerPath = path.toLowerCase();

        // Buckets spécifiques
        if (lowerPath.startsWith("restaurants/") || lowerPath.contains("restaurant")) {
            return "restaurants";
        } else if (lowerPath.startsWith("plats/") || lowerPath.contains("plat")) {
            return "plats";
        } else if (lowerPath.startsWith("avatars/") || lowerPath.contains("avatar")) {
            return "avatars";
        } else if (lowerPath.startsWith("categories/") || lowerPath.contains("categor")) {
            return "categories";
        }

        // Bucket par défaut
        return imageBucket;
    }
}
