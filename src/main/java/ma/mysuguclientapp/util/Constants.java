package ma.mysuguclientapp.util;

/**
 * Constantes de l'application
 */
public final class Constants {

    private Constants() {
        // Empêcher l'instanciation
    }

    // ========== FICHIERS ==========
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    public static final String[] ALLOWED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"};
    
    // ========== MINIO BUCKETS ==========
    public static final String BUCKET_RESTAURANTS = "restaurant-images";
    public static final String BUCKET_PLATS = "plats";
    public static final String BUCKET_AVATARS = "avatars";
    public static final String BUCKET_CATEGORIES = "categories";
    
    // ========== LIVRAISON ==========
    public static final double DEFAULT_DELIVERY_RADIUS_KM = 10.0;
    public static final double DEFAULT_AVERAGE_SPEED_KMH = 30.0;
    public static final int BASE_DELIVERY_TIME_MINUTES = 10;
    public static final int BASE_DELIVERY_FEE_CFA = 500;
    public static final int DELIVERY_FEE_PER_KM_CFA = 200;
    
    // ========== COMMANDES ==========
    public static final String COMMANDE_PREFIX = "CMD";
    public static final int MAX_ITEMS_PER_COMMANDE = 50;
    
    // ========== PAGINATION ==========
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;
    
    // ========== VALIDATIONS ==========
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_PASSWORD_LENGTH = 100;
    public static final int MIN_TELEPHONE_LENGTH = 8;
    public static final int MAX_TELEPHONE_LENGTH = 20;
    
    // ========== RATINGS ==========
    public static final double MIN_RATING = 0.0;
    public static final double MAX_RATING = 5.0;
    
    // ========== MESSAGES ==========
    public static final String ERROR_RESOURCE_NOT_FOUND = "Ressource non trouvée";
    public static final String ERROR_UNAUTHORIZED = "Non autorisé";
    public static final String ERROR_INVALID_CREDENTIALS = "Email ou mot de passe incorrect";
    public static final String ERROR_ACCOUNT_DISABLED = "Compte désactivé";
    public static final String ERROR_EMAIL_ALREADY_EXISTS = "Un utilisateur avec cet email existe déjà";
    public static final String ERROR_INVALID_FILE_TYPE = "Type de fichier non supporté";
    public static final String ERROR_FILE_TOO_LARGE = "Fichier trop volumineux";
    
    // ========== RÔLES ==========
    public static final String ROLE_CLIENT = "CLIENT";
    public static final String ROLE_LIVREUR = "LIVREUR";
    public static final String ROLE_RESTAURANT_OWNER = "RESTAURANT_OWNER";
    public static final String ROLE_ADMIN = "ADMIN";
    
    // ========== DATE/TIME FORMATS ==========
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String TIME_FORMAT = "HH:mm";
    
    // ========== CURRENCY ==========
    public static final String CURRENCY = "XOF"; // Franc CFA
    public static final String CURRENCY_SYMBOL = "FCFA";
}
