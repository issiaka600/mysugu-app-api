package ma.mysuguclientapp.exceptions;

/** An application error with a stable code; provider details and keys never reach the client. */
public class LocationContractException extends RuntimeException {
    private final int status;
    private final String code;

    public LocationContractException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }

    public static LocationContractException required() {
        return new LocationContractException(422, "RESTAURANT_LOCATION_REQUIRED",
                "Précisez l’adresse ou choisissez le point sur la carte.");
    }

    public static LocationContractException unavailable(boolean routing) {
        return new LocationContractException(503,
                routing ? "GOOGLE_ROUTES_UNAVAILABLE" : "GEOCODING_UNAVAILABLE",
                routing ? "Le calcul de l’itinéraire est indisponible. Réessayez."
                        : "La recherche d’adresse est indisponible. Réessayez.");
    }
}
