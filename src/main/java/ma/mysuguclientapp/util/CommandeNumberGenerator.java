package ma.mysuguclientapp.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Générateur de numéros de commande uniques
 */
public class CommandeNumberGenerator {

    private static final Random random = new Random();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Génère un numéro de commande unique
     * Format: CMD-YYYYMMDDHHMMSS-XXXX
     * Exemple: CMD-20240216143052-4782
     */
    public static String generate() {
        String timestamp = LocalDateTime.now().format(formatter);
        int randomNumber = 1000 + random.nextInt(9000); // Nombre entre 1000 et 9999
        
        return String.format("CMD-%s-%04d", timestamp, randomNumber);
    }

    /**
     * Génère un numéro de commande avec préfixe personnalisé
     */
    public static String generate(String prefix) {
        String timestamp = LocalDateTime.now().format(formatter);
        int randomNumber = 1000 + random.nextInt(9000);
        
        return String.format("%s-%s-%04d", prefix, timestamp, randomNumber);
    }
}
