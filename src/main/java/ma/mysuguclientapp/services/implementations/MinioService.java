package ma.mysuguclientapp.services.implementations;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.FileMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private static final String FOLDER_RESTAURANTS = "restaurants";
    private static final String FOLDER_PLATS = "plats";
    private static final String FOLDER_AVATARS = "avatars";
    private static final String FOLDER_CATEGORIES = "categories";
    private static final String FOLDER_UPLOADS = "uploads";

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    public void initBuckets() {
        try {
            createBucketIfNotExists(bucketName);
            log.info("Bucket MinIO initialisé avec succès: {}", bucketName);
        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation du bucket MinIO", e);
        }
    }

    private void createBucketIfNotExists(String bucket) throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("Bucket créé: {}", bucket);
        }
    }

    public String uploadFile(MultipartFile file, String folder) throws Exception {
        String normalizedFolder = normalizeFolder(folder);
        String fileName = generateFileName(file.getOriginalFilename());
        String objectName = normalizedFolder + "/" + fileName;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("Fichier uploadé: {} dans le bucket {}", objectName, bucketName);
            return objectName;
        }
    }

    public String getFileUrl(String objectName) {
        return buildPublicFileUrl(objectName);
    }

    public void deleteFile(String objectName) throws Exception {
        String normalizedObjectName = normalizeObjectName(objectName);

        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(normalizedObjectName)
                        .build()
        );
        log.info("Fichier supprimé: {} du bucket {}", normalizedObjectName, bucketName);
    }

    public InputStream downloadFile(String objectName) throws Exception {
        String normalizedObjectName = normalizeObjectName(objectName);

        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(normalizedObjectName)
                        .build()
        );
    }

    public boolean fileExists(String objectName) {
        try {
            String normalizedObjectName = normalizeObjectName(objectName);

            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(normalizedObjectName)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public FileMetadata getFileMetadata(String objectName) throws Exception {
        String normalizedObjectName = normalizeObjectName(objectName);

        var stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(normalizedObjectName)
                        .build()
        );

        FileMetadata metadata = new FileMetadata();
        metadata.setObjectName(normalizedObjectName);
        metadata.setBucket(bucketName);
        metadata.setFileName(extractFileName(normalizedObjectName));
        metadata.setSize(stat.size());
        metadata.setContentType(stat.contentType());
        metadata.setUrl(buildPublicFileUrl(normalizedObjectName));
        metadata.setDownloadUrl(buildPublicFileUrl(normalizedObjectName) + "?download=true");
        metadata.setLastModified(stat.lastModified().toLocalDateTime());
        return metadata;
    }

    public String buildPublicFileUrl(String objectName) {
        String normalizedObjectName = normalizeObjectName(objectName);
        if (normalizedObjectName == null || normalizedObjectName.isBlank()) {
            return null;
        }

        String path = "/api/files/" + normalizedObjectName;
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return path;
        }

        return publicBaseUrl.replaceAll("/+$", "") + path;
    }

    public String resolveBucket(String ignoredPath) {
        return bucketName;
    }

    public String normalizeObjectName(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return objectName;
        }

        String normalized = objectName.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "");

        normalized = stripBucketPrefix(normalized, bucketName);

        if (normalized.startsWith(FOLDER_RESTAURANTS + "/")
                || normalized.startsWith(FOLDER_PLATS + "/")
                || normalized.startsWith(FOLDER_AVATARS + "/")
                || normalized.startsWith(FOLDER_CATEGORIES + "/")
                || normalized.startsWith(FOLDER_UPLOADS + "/")) {
            return normalized;
        }

        return normalized;
    }

    private String stripBucketPrefix(String objectName, String bucket) {
        if (bucket == null || bucket.isBlank()) {
            return objectName;
        }

        String prefix = bucket + "/";
        String lowerObjectName = objectName.toLowerCase();
        String lowerPrefix = prefix.toLowerCase();

        if (lowerObjectName.startsWith(lowerPrefix)) {
            return objectName.substring(prefix.length());
        }

        return objectName;
    }

    private String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return FOLDER_UPLOADS;
        }

        String normalized = folder.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "");
        if (normalized.startsWith("restaurants/")) {
            return normalized;
        }
        if (normalized.equals("restaurants")) {
            return FOLDER_RESTAURANTS;
        }
        if (normalized.startsWith("plats/")) {
            return normalized;
        }
        if (normalized.equals("plats")) {
            return FOLDER_PLATS;
        }
        if (normalized.startsWith("avatars/")) {
            return normalized;
        }
        if (normalized.equals("avatars")) {
            return FOLDER_AVATARS;
        }
        if (normalized.startsWith("categories/")) {
            return normalized;
        }
        if (normalized.equals("categories")) {
            return FOLDER_CATEGORIES;
        }
        if (normalized.startsWith("uploads/")) {
            return normalized;
        }

        return FOLDER_UPLOADS + "/" + normalized;
    }

    private String extractFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private String generateFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID() + extension;
    }
}
