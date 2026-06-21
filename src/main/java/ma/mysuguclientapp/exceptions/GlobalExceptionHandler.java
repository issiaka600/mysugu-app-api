package ma.mysuguclientapp.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Gestion des ressources non trouvées (404)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        
        log.error("Ressource non trouvée: {}", ex.getMessage());
        return new ResponseEntity<>(buildError(
                HttpStatus.NOT_FOUND,
                "Not Found",
                ex.getMessage(),
                request),
                HttpStatus.NOT_FOUND);
    }

    /**
     * Gestion des requêtes invalides (400)
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(
            BadRequestException ex, WebRequest request) {
        
        log.error("Requête invalide: {}", ex.getMessage());
        return new ResponseEntity<>(buildError(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                ex.getMessage(),
                request),
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Gestion des erreurs d'authentification (401)
     */
    @ExceptionHandler({UnauthorizedException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            Exception ex, WebRequest request) {
        
        log.error("Erreur d'authentification: {}", ex.getMessage());
        return new ResponseEntity<>(buildError(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                ex.getMessage(),
                request),
                HttpStatus.UNAUTHORIZED);
    }

    /**
     * Gestion des erreurs d'autorisation (403)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        
        log.error("Accès refusé: {}", ex.getMessage());
        return new ResponseEntity<>(buildError(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "Vous n'avez pas les permissions nécessaires pour accéder à cette ressource",
                request),
                HttpStatus.FORBIDDEN);
    }

    /**
     * Gestion des erreurs de validation (400)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.error("Erreurs de validation: {}", errors);
        ErrorResponse errorResponse = buildError(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                "Erreurs de validation",
                request);
        errorResponse.setValidationErrors(errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Gestion des fichiers trop volumineux (413)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, WebRequest request) {
        
        log.error("Fichier trop volumineux: {}", ex.getMessage());
        return new ResponseEntity<>(buildError(
                HttpStatus.CONTENT_TOO_LARGE,
                "Payload Too Large",
                "Le fichier chargé est trop volumineux. Taille maximale: 10MB",
                request),
                HttpStatus.CONTENT_TOO_LARGE);
    }

    /**
     * Gestion des erreurs génériques (500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, WebRequest request) {
        
        log.error("Erreur interne du serveur", ex);
        return new ResponseEntity<>(buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "Une erreur inattendue s'est produite",
                request),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ErrorResponse buildError(HttpStatus status, String error, String message, WebRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                error,
                message,
                request.getDescription(false).replace("uri=", ""),
                null
        );
    }

}
