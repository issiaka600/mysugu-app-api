package ma.mysuguclientapp.util;

/**
 * Utilitaire pour calculer les distances géographiques
 */
public class DistanceCalculator {

    private static final int EARTH_RADIUS_KM = 6371;

    /**
     * Calcule la distance entre deux points géographiques en utilisant la formule de Haversine
     * 
     * @param lat1 Latitude du point 1
     * @param lon1 Longitude du point 1
     * @param lat2 Latitude du point 2
     * @param lon2 Longitude du point 2
     * @return Distance en kilomètres
     */
    public static double calculate(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return Double.MAX_VALUE;
        }

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Calcule la distance et arrondit au nombre de décimales spécifié
     */
    public static double calculateAndRound(Double lat1, Double lon1, Double lat2, Double lon2, int decimals) {
        double distance = calculate(lat1, lon1, lat2, lon2);
        double factor = Math.pow(10, decimals);
        return Math.round(distance * factor) / factor;
    }

    /**
     * Vérifie si deux points sont dans un rayon donné
     */
    public static boolean isWithinRadius(Double lat1, Double lon1, Double lat2, Double lon2, double radiusKm) {
        double distance = calculate(lat1, lon1, lat2, lon2);
        return distance <= radiusKm;
    }

    /**
     * Calcule le temps de trajet estimé basé sur la distance
     * Hypothèse: vitesse moyenne de 30 km/h en ville
     */
    public static int calculateEstimatedTravelTime(Double lat1, Double lon1, Double lat2, Double lon2) {
        double distance = calculate(lat1, lon1, lat2, lon2);
        double averageSpeedKmh = 30.0;
        
        // Temps en minutes
        int travelTime = (int) Math.ceil((distance / averageSpeedKmh) * 60);
        
        // Ajouter un temps de base (préparation, attente, etc.)
        return travelTime + 10;
    }
}
