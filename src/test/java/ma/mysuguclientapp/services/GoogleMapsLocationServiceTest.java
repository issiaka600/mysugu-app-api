package ma.mysuguclientapp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import ma.mysuguclientapp.exceptions.LocationContractException;
import ma.mysuguclientapp.services.location.GoogleMapsLocationService;
import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class GoogleMapsLocationServiceTest {
    HttpServer server;
    ExecutorService executor;
    GoogleMapsLocationService maps;
    String body, requestBody, query, fieldMask, apiKey;
    int status;
    volatile int delayMs;
    AtomicInteger calls;

    @BeforeEach void start() throws Exception {
        status = 200; delayMs = 0; calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(); server.setExecutor(executor);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            query = exchange.getRequestURI().getRawQuery();
            fieldMask = exchange.getRequestHeaders().getFirst("X-Goog-FieldMask");
            apiKey = exchange.getRequestHeaders().getFirst("X-Goog-Api-Key");
            if (delayMs > 0) {
                try { Thread.sleep(delayMs); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, data.length);
            exchange.getResponseBody().write(data); exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        maps = new GoogleMapsLocationService("unit-key", "unit-key", url + "/geo", url + "/routes", 1000);
    }
    @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }

    String result(String type, boolean partial) {
        return "{\"types\":[\"street_address\"],\"partial_match\":" + partial
                + ",\"geometry\":{\"location_type\":\"" + type
                + "\",\"location\":{\"lat\":31.6,\"lng\":-8.02}}}";
    }
    void error(Runnable call, int expected, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(LocationContractException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(expected);
            assertThat(ex.getCode()).isEqualTo(code);
            assertThat(ex.getMessage()).doesNotContain("unit-key");
        });
    }
    @Test void exactBuildingMatchAndUnicodeAddress() {
        body = "{\"status\":\"OK\",\"results\":[" + result("ROOFTOP", false) + "]}";
        var point = maps.geocode("  Rue de l’École & Café  ");
        assertThat(point.latitude()).isEqualTo(31.6); assertThat(point.longitude()).isEqualTo(-8.02);
        assertThat(query).contains("address=Rue", "%26", "language=fr");
    }
    @Test void ambiguousPartialCoarseAndUnknownAddressesRequireMap() {
        for (String results : new String[]{"[]", "[" + result("ROOFTOP", false) + "," + result("ROOFTOP", false) + "]",
                "[" + result("ROOFTOP", true) + "]", "[" + result("APPROXIMATE", false) + "]",
                "[" + result("RANGE_INTERPOLATED", false) + "]"}) {
            body = "{\"status\":\"OK\",\"results\":" + results + "}";
            error(() -> maps.geocode("Rue test"), 422, "RESTAURANT_LOCATION_REQUIRED");
        }
        body = "{\"status\":\"ZERO_RESULTS\",\"results\":[]}";
        error(() -> maps.geocode("Inconnue"), 422, "RESTAURANT_LOCATION_REQUIRED");
    }
    @Test void providerFailureAndMalformedGeocodingResponseAreRetryable() {
        for (String failure : new String[]{"{\"status\":\"OVER_QUERY_LIMIT\"}", "{\"status\":\"REQUEST_DENIED\"}", "oops",
                "{\"status\":\"OK\",\"results\":[{\"types\":[\"street_address\"],\"geometry\":{\"location_type\":\"ROOFTOP\",\"location\":{}}}]}"}) {
            body = failure; error(() -> maps.geocode("Rue test"), 503, "GEOCODING_UNAVAILABLE");
        }
        status = 500; body = "{}";
        error(() -> maps.geocode("Rue test"), 503, "GEOCODING_UNAVAILABLE");
    }
    @Test void routesRequestsSavedPointsAndConvertsMetersWithoutRounding() throws Exception {
        body = "{\"routes\":[{\"distanceMeters\":5251}]}";
        assertThat(maps.routeKm(31.6, -8.02, 31.61, -8.03)).isEqualByComparingTo("5.251");
        var request = new ObjectMapper().readTree(requestBody);
        assertThat(request.at("/origin/location/latLng/latitude").asDouble()).isEqualTo(31.6);
        assertThat(request.at("/destination/location/latLng/longitude").asDouble()).isEqualTo(-8.03);
        assertThat(request.path("travelMode").asText()).isEqualTo("DRIVE");
        assertThat(fieldMask).isEqualTo("routes.distanceMeters"); assertThat(apiKey).isEqualTo("unit-key");
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void routeFailureNeverReturnsAnEstimatedDistance() {
        for (String failure : new String[]{"{\"routes\":[]}", "{}", "oops", "{\"routes\":[{\"distanceMeters\":-1}]}",
                "{\"routes\":[{\"distanceMeters\":\"5251\"}]}"}) {
            body = failure; error(() -> maps.routeKm(31.6, -8.02, 31.61, -8.03), 503, "GOOGLE_ROUTES_UNAVAILABLE");
        }
        status = 429; body = "{}";
        error(() -> maps.routeKm(31.6, -8.02, 31.61, -8.03), 503, "GOOGLE_ROUTES_UNAVAILABLE");
    }
    @Test void providerTimeoutIsRetryable() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        maps = new GoogleMapsLocationService("unit-key", "unit-key", url + "/geo", url + "/routes", 100);
        delayMs = 500; body = "{}";
        error(() -> maps.geocode("Rue test"), 503, "GEOCODING_UNAVAILABLE");
        error(() -> maps.routeKm(31.6, -8.02, 31.61, -8.03), 503, "GOOGLE_ROUTES_UNAVAILABLE");
    }
    @Test void missingCredentialsAndNetworkFailureAreRetryable() {
        maps = new GoogleMapsLocationService("", "", "http://127.0.0.1:1", "http://127.0.0.1:1", 100);
        error(() -> maps.geocode("Rue test"), 503, "GEOCODING_UNAVAILABLE");
        error(() -> maps.routeKm(31.6, -8.02, 31.61, -8.03), 503, "GOOGLE_ROUTES_UNAVAILABLE");
        assertThat(calls.get()).isZero();
        maps = new GoogleMapsLocationService("unit-key", "unit-key", "http://127.0.0.1:1", "http://127.0.0.1:1", 100);
        error(() -> maps.geocode("Rue test"), 503, "GEOCODING_UNAVAILABLE");
        error(() -> maps.routeKm(31.6, -8.02, 31.61, -8.03), 503, "GOOGLE_ROUTES_UNAVAILABLE");
    }
}
