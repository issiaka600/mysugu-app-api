package ma.mysuguclientapp.services.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.exceptions.LocationContractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Service
public class GoogleMapsLocationService {
    public record Point(double latitude, double longitude) {}
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http;
    private final String geocodingKey, routesKey, geocodingUrl, routesUrl;
    private final Duration timeout;

    public GoogleMapsLocationService(
            @Value("${google.maps.geocoding-key:${google.maps.api-key:}}") String geocodingKey,
            @Value("${google.maps.routes-key:${google.maps.api-key:}}") String routesKey,
            @Value("${google.maps.geocoding-url:https://maps.googleapis.com/maps/api/geocode/json}") String geocodingUrl,
            @Value("${google.maps.routes-url:https://routes.googleapis.com/directions/v2:computeRoutes}") String routesUrl,
            @Value("${google.maps.timeout-ms:5000}") long timeoutMs) {
        this.geocodingKey = geocodingKey;
        this.routesKey = routesKey;
        this.geocodingUrl = geocodingUrl;
        this.routesUrl = routesUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /** Only one complete, building-level match is accepted automatically. */
    public Point geocode(String address) {
        if (address == null || address.isBlank()) throw LocationContractException.required();
        requireKey(geocodingKey, false);
        try {
            String url = geocodingUrl + "?address=" + encode(address.trim())
                    + "&language=fr&key=" + encode(geocodingKey);
            JsonNode body = send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build(), false);
            String status = body.path("status").asText();
            if ("ZERO_RESULTS".equals(status)) throw LocationContractException.required();
            if (!"OK".equals(status)) throw LocationContractException.unavailable(false);
            JsonNode results = body.path("results");
            if (!results.isArray()) throw LocationContractException.unavailable(false);
            if (results.size() != 1) throw LocationContractException.required();
            JsonNode result = results.get(0);
            if (result.path("partial_match").asBoolean()
                    || !"ROOFTOP".equals(result.path("geometry").path("location_type").asText())
                    || !buildingType(result.path("types"))) throw LocationContractException.required();
            JsonNode point = result.path("geometry").path("location");
            if (!point.path("lat").isNumber() || !point.path("lng").isNumber())
                throw LocationContractException.unavailable(false);
            double lat = point.get("lat").asDouble(), lon = point.get("lng").asDouble();
            if (!validCoordinates(lat, lon)) throw LocationContractException.unavailable(false);
            return new Point(lat, lon);
        } catch (LocationContractException ex) {
            throw ex;
        } catch (Exception ex) {
            throw LocationContractException.unavailable(false);
        }
    }

    /** The sole distance used for delivery pricing. No straight-line fallback. */
    public BigDecimal routeKm(double originLat, double originLon, double destinationLat, double destinationLon) {
        if (!validCoordinates(originLat, originLon) || !validCoordinates(destinationLat, destinationLon))
            throw LocationContractException.unavailable(true);
        requireKey(routesKey, true);
        try {
            Map<String, Object> payload = Map.of(
                    "origin", waypoint(originLat, originLon),
                    "destination", waypoint(destinationLat, destinationLon),
                    "travelMode", "DRIVE", "routingPreference", "TRAFFIC_UNAWARE",
                    "computeAlternativeRoutes", false, "units", "METRIC");
            HttpRequest request = HttpRequest.newBuilder(URI.create(routesUrl)).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", routesKey)
                    .header("X-Goog-FieldMask", "routes.distanceMeters")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            JsonNode routes = send(request, true).path("routes");
            if (!routes.isArray() || routes.isEmpty()) throw LocationContractException.unavailable(true);
            JsonNode meters = routes.get(0).path("distanceMeters");
            if (!meters.isIntegralNumber() || meters.decimalValue().signum() < 0)
                throw LocationContractException.unavailable(true);
            return meters.decimalValue().movePointLeft(3);
        } catch (LocationContractException ex) {
            throw ex;
        } catch (Exception ex) {
            throw LocationContractException.unavailable(true);
        }
    }

    private JsonNode send(HttpRequest request, boolean routing) throws Exception {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw LocationContractException.unavailable(routing);
        }
        if (response.statusCode() != 200) throw LocationContractException.unavailable(routing);
        return json.readTree(response.body());
    }

    public static boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    private static boolean buildingType(JsonNode types) {
        Set<String> accepted = Set.of("street_address", "premise", "subpremise", "establishment");
        for (JsonNode type : types) if (accepted.contains(type.asText())) return true;
        return false;
    }
    private static Map<String, Object> waypoint(double latitude, double longitude) {
        return Map.of("location", Map.of("latLng", Map.of("latitude", latitude, "longitude", longitude)));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static void requireKey(String key, boolean routing) {
        if (key == null || key.isBlank()) throw LocationContractException.unavailable(routing);
    }
}
