package ma.mysuguclientapp.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.FileUrlResponse;
import ma.mysuguclientapp.services.implementations.MinioService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

/**
 * Controller pour servir les fichiers stockés dans MinIO
 * Version simplifiée avec AntPathMatcher
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileControllerSimple {

    private final MinioService minioService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * GET /api/files/{bucket}/**
     * Récupère un fichier depuis MinIO
     * Exemple : GET /api/files/restaurants/logos/abc123.jpg
     */
    @GetMapping("/{bucket}/**")
    public ResponseEntity<InputStreamResource> getFile(
            @PathVariable String bucket,
            @RequestParam(required = false, defaultValue = "false") boolean download,
            HttpServletRequest request) {
        
        try {
            // Extraire le chemin du fichier
            String path = request.getRequestURI();
            String pattern = "/api/files/" + bucket + "/**";
            String filePath = pathMatcher.extractPathWithinPattern(pattern, path);
            
            // Construire l'objectName complet
            String objectName = bucket + "/" + filePath;
            
            log.info("Récupération du fichier: {}", objectName);
            
            // Télécharger le fichier depuis MinIO
            InputStream inputStream = minioService.downloadFile(objectName);
            InputStreamResource resource = new InputStreamResource(inputStream);
            
            // Déterminer le type de contenu
            String contentType = determineContentType(filePath);
            
            // Construire la réponse
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            
            // Si download=true, forcer le téléchargement
            if (download) {
                String filename = getFileName(filePath);
                headers.setContentDispositionFormData("attachment", filename);
            } else {
                // Sinon, affichage inline (dans le navigateur)
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline");
            }
            
            // Cache pour 7 jours
            headers.setCacheControl("max-age=604800, public");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("Erreur lors de la récupération du fichier depuis MinIO", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * GET /api/files/url/{bucket}/**
     * Génère une URL pré-signée pour accéder au fichier
     * Exemple : GET /api/files/url/restaurants/logos/abc123.jpg
     */
    @GetMapping("/url/{bucket}/**")
    public ResponseEntity<FileUrlResponse> getFileUrl(
            @PathVariable String bucket,
            HttpServletRequest request) {
        
        try {
            // Extraire le chemin du fichier
            String path = request.getRequestURI();
            String pattern = "/api/files/url/" + bucket + "/**";
            String filePath = pathMatcher.extractPathWithinPattern(pattern, path);
            
            String objectName = bucket + "/" + filePath;
            
            log.info("Génération d'URL pré-signée pour: {}", objectName);
            
            // Générer l'URL pré-signée (valide 7 jours)
            String url = minioService.getFileUrl(objectName);
            
            FileUrlResponse response = new FileUrlResponse();
            response.setUrl(url);
            response.setObjectName(objectName);
            response.setExpiresIn(604800); // 7 jours en secondes
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Erreur lors de la génération de l'URL", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Extrait le nom du fichier depuis le chemin complet
     */
    private String getFileName(String filePath) {
        int lastSlashIndex = filePath.lastIndexOf('/');
        return (lastSlashIndex >= 0) ? filePath.substring(lastSlashIndex + 1) : filePath;
    }

    /**
     * Détermine le type MIME du fichier basé sur l'extension
     */
    private String determineContentType(String filename) {
        String lowerFilename = filename.toLowerCase();
        
        // Images
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
        }
        
        // Documents
        else if (lowerFilename.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFilename.endsWith(".doc")) {
            return "application/msword";
        } else if (lowerFilename.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        
        // Vidéos
        else if (lowerFilename.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lowerFilename.endsWith(".webm")) {
            return "video/webm";
        }
        
        // Par défaut
        return "application/octet-stream";
    }
}
