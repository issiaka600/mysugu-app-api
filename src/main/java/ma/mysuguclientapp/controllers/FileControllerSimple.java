package ma.mysuguclientapp.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.FileMetadata;
import ma.mysuguclientapp.dtos.FileUploadResponse;
import ma.mysuguclientapp.dtos.FileUrlResponse;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.services.implementations.MinioService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import java.io.InputStream;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileControllerSimple {

    private static final int DEFAULT_URL_EXPIRY = 0;

    private final MinioService minioService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "uploads") String folder) {

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Le fichier est requis");
        }

        try {
            String objectName = minioService.uploadFile(file, folder);
            FileMetadata metadata = minioService.getFileMetadata(objectName);

            FileUploadResponse response = new FileUploadResponse();
            response.setObjectName(objectName);
            response.setBucket(metadata.getBucket());
            response.setFileName(metadata.getFileName());
            response.setSize(metadata.getSize());
            response.setContentType(metadata.getContentType());
            response.setUrl(metadata.getUrl());
            response.setDownloadUrl(metadata.getDownloadUrl());
            response.setPresignedUrl(metadata.getUrl());
            response.setExpiresIn(DEFAULT_URL_EXPIRY);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Erreur lors de l'upload du fichier", e);
            throw new BadRequestException("Impossible d'uploader le fichier", e);
        }
    }

    @GetMapping
    public ResponseEntity<InputStreamResource> getFileByQuery(
            @RequestParam String objectName,
            @RequestParam(required = false, defaultValue = "false") boolean download) {
        return getFile(objectName, download);
    }

    @GetMapping("/{*objectName}")
    public ResponseEntity<InputStreamResource> getFile(
            @PathVariable String objectName,
            @RequestParam(required = false, defaultValue = "false") boolean download) {

        try {
            String normalizedObjectName = minioService.normalizeObjectName(objectName);
            InputStream inputStream = minioService.downloadFile(normalizedObjectName);
            InputStreamResource resource = new InputStreamResource(inputStream);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(determineContentType(normalizedObjectName)));

            if (download) {
                headers.setContentDispositionFormData("attachment", getFileName(normalizedObjectName));
            } else {
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline");
            }

            headers.setCacheControl("max-age=604800, public");
            return ResponseEntity.ok().headers(headers).body(resource);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération du fichier", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/metadata/{*objectName}")
    public ResponseEntity<FileMetadata> getFileMetadata(@PathVariable String objectName) {
        try {
            return ResponseEntity.ok(minioService.getFileMetadata(objectName));
        } catch (Exception e) {
            log.error("Erreur lors de la lecture des métadonnées", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/metadata")
    public ResponseEntity<FileMetadata> getFileMetadataByQuery(@RequestParam String objectName) {
        return getFileMetadata(objectName);
    }

    @GetMapping("/url/{*objectName}")
    public ResponseEntity<FileUrlResponse> getFileUrl(@PathVariable String objectName) {
        try {
            String normalizedObjectName = minioService.normalizeObjectName(objectName);

            FileUrlResponse response = new FileUrlResponse();
            response.setObjectName(normalizedObjectName);
            response.setBucket(minioService.resolveBucket(normalizedObjectName));
            response.setFileName(getFileName(normalizedObjectName));
            response.setUrl(minioService.buildPublicFileUrl(normalizedObjectName));
            response.setDownloadUrl(minioService.buildPublicFileUrl(normalizedObjectName) + "?download=true");
            response.setPresignedUrl(minioService.buildPublicFileUrl(normalizedObjectName));
            response.setExpiresIn(DEFAULT_URL_EXPIRY);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur lors de la génération d'URL", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/url")
    public ResponseEntity<FileUrlResponse> getFileUrlByQuery(@RequestParam String objectName) {
        return getFileUrl(objectName);
    }

    @GetMapping("/legacy/{bucket}/**")
    public ResponseEntity<InputStreamResource> getFileLegacy(
            @PathVariable String bucket,
            @RequestParam(required = false, defaultValue = "false") boolean download,
            HttpServletRequest request) {
        String pathWithinMapping = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String nestedPath = new org.springframework.util.AntPathMatcher().extractPathWithinPattern(bestPattern, pathWithinMapping);
        return getFile(bucket + "/" + nestedPath, download);
    }

    private String getFileName(String filePath) {
        int lastSlashIndex = filePath.lastIndexOf('/');
        return (lastSlashIndex >= 0) ? filePath.substring(lastSlashIndex + 1) : filePath;
    }

    private String determineContentType(String filename) {
        String lowerFilename = filename.toLowerCase();

        if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFilename.endsWith(".png")) {
            return "image/png";
        } else if (lowerFilename.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFilename.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerFilename.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (lowerFilename.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFilename.endsWith(".doc")) {
            return "application/msword";
        } else if (lowerFilename.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lowerFilename.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lowerFilename.endsWith(".webm")) {
            return "video/webm";
        }

        return "application/octet-stream";
    }
}
