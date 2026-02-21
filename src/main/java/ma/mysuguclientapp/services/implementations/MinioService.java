package ma.mysuguclientapp.services.implementations;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            log.info("Buckets MinIO initialisés avec succès");
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
            log.info("Bucket créé: {}", bucket);
        }
    }

    /**
     * Upload un fichier dans MinIO
     */
    public String uploadFile(MultipartFile file, String folder) throws Exception {
        String fileName = generateFileName(file.getOriginalFilename());
        String objectName = folder + "/" + fileName;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(imageBucket)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );
            
            log.info("Fichier uploadé: {}", objectName);
            return objectName;
        }
    }

    /**
     * Obtenir l'URL de téléchargement d'un fichier (URL pré-signée)
     */
    public String getFileUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(imageBucket)
                .object(objectName)
                .expiry(7, TimeUnit.DAYS)
                .build()
        );
    }

    /**
     * Supprimer un fichier
     */
    public void deleteFile(String objectName) throws Exception {
        minioClient.removeObject(
            RemoveObjectArgs.builder()
                .bucket(imageBucket)
                .object(objectName)
                .build()
        );
        log.info("Fichier supprimé: {}", objectName);
    }

    /**
     * Télécharger un fichier
     */
    public InputStream downloadFile(String objectName) throws Exception {
        return minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(imageBucket)
                .object(objectName)
                .build()
        );
    }

    /**
     * Générer un nom de fichier unique
     */
    private String generateFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID() + extension;
    }
}
