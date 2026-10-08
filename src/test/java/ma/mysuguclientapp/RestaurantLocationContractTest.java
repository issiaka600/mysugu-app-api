package ma.mysuguclientapp;

import com.fasterxml.jackson.databind.*;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.*;
import ma.mysuguclientapp.repositories.*;
import ma.mysuguclientapp.services.chat.ConversationService;
import ma.mysuguclientapp.services.implementations.*;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "dispatch.auto.enabled=false")
class RestaurantLocationContractTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired RestaurantRepository restaurants;
    @Autowired CommandeRepository orders;
    @Autowired ConversationService chat;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean MinioService minio;
    @MockitoBean FcmService fcm;
    @MockitoBean EmailService email;
    @MockitoBean ma.mysuguclientapp.services.implementations.NotificationServiceImpl notifications;
    final ObjectMapper json = new ObjectMapper();
    final HttpClient http = HttpClient.newHttpClient();
    Long restaurantId, orderId;
    String adminToken, driverToken, otherDriverToken;
    static final double OLD_LAT = 31.6540716, OLD_LNG = -8.0095317;
    // Valeurs de test, sans prétendre localiser l'entrée du restaurant réel.
    static final double NEW_LAT = 31.60, NEW_LNG = -8.02;

    @BeforeEach
    void seed() {
        User admin = user(UserRole.ADMIN);
        User driver = user(UserRole.LIVREUR);
        User other = user(UserRole.LIVREUR);
        User owner = user(UserRole.RESTAURANT_OWNER);
        User client = user(UserRole.CLIENT);
        adminToken = jwt.generateToken(admin);
        driverToken = jwt.generateToken(driver);
        otherDriverToken = jwt.generateToken(other);
        Restaurant r = new Restaurant();
        r.setNom("GPS test " + System.nanoTime());
        r.setOwner(owner);
        r.setIsActive(true);
        r.setLocalisation(new Localisation(OLD_LAT, OLD_LNG, "Ancienne adresse",
                "Marrakech", "40090", "Maroc"));
        restaurants.save(r);
        restaurantId = r.getId();
        Commande c = new Commande();
        c.setNumeroCommande("GPS-" + System.nanoTime());
        c.setRestaurant(r); c.setClient(client); c.setLivreur(driver);
        c.setStatut(StatutCommande.ASSIGNEE_LIVREUR);
        c.setStatutPaiement(StatutPaiement.EN_ATTENTE);
        c.setModeReception(ModeReceptionCommande.LIVRAISON);
        c.setMontantTotal(BigDecimal.TEN); c.setMontantFinal(BigDecimal.TEN);
        c.setFraisLivraison(BigDecimal.ZERO);
        orderId = orders.save(c).getId();
        chat.append(new ParticipantRef(ParticipantType.RESTAURANT, restaurantId),
                new ParticipantRef(ParticipantType.LIVREUR, driver.getId()),
                "Point de retrait", List.of());
    }

    @Test
    void nestedBackOfficeCoordinatesPersistAndRefreshBothDriverResponses() throws Exception {
        assertDriverPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
        var response = update(Map.of(
                "localisation.adresse", "Nouvelle entrée",
                "localisation.latitude", String.valueOf(NEW_LAT),
                "localisation.longitude", String.valueOf(NEW_LNG)));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertSavedPoint(NEW_LAT, NEW_LNG, "Nouvelle entrée");
        // Même commande et conversation, créées avant la modification.
        assertDriverPoint(NEW_LAT, NEW_LNG, "Nouvelle entrée");
    }

    @Test
    void flatBackOfficeCoordinatesPersist() throws Exception {
        var response = update(Map.of("adresse", "Entrée test",
                "latitude", String.valueOf(NEW_LAT), "longitude", String.valueOf(NEW_LNG)));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertSavedPoint(NEW_LAT, NEW_LNG, "Entrée test");
        assertDriverPoint(NEW_LAT, NEW_LNG, "Entrée test");
    }

    @Test
    void addressOnlyChangeRejectedWithoutSavingOldGpsUnderNewAddress() throws Exception {
        var response = update(Map.of("localisation.adresse", "Adresse déplacée"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("point GPS");
        assertSavedPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
        assertDriverPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
    }

    @Test
    void partialOrInvalidCoordinatesRejected() throws Exception {
        for (Map<String, String> fields : List.of(
                Map.of("latitude", "31.5"),
                Map.of("longitude", "-8.1"),
                Map.of("latitude", "91", "longitude", "0"),
                Map.of("latitude", "0", "longitude", "-181"),
                Map.of("latitude", "NaN", "longitude", "0"))) {
            var response = update(fields);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertSavedPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
        }
    }

    @Test
    void unrelatedUpdateKeepsCoordinatesAndUnassignedDriverCannotReadLocation() throws Exception {
        var response = update(Map.of("description", "Nouvelle description"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertSavedPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
        response = get("/api/v2/delivery-man/seller-location?order_id=" + orderId, otherDriverToken);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
    }

    private void assertDriverPoint(double lat, double lng, String address) throws Exception {
        var response = get("/api/v2/delivery-man/seller-location?order_id=" + orderId, driverToken);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode gps = json.readTree(response.body());
        assertThat(Double.parseDouble(gps.get("latitude").asText())).isEqualTo(lat);
        assertThat(Double.parseDouble(gps.get("longitude").asText())).isEqualTo(lng);
        response = get("/api/v2/delivery-man/messages/list/seller", driverToken);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode info = json.readTree(response.body()).get("chat").get(0).get("seller_info");
        JsonNode shop = info.get("shop");
        assertThat(shop.get("id").asLong()).isEqualTo(restaurantId);
        assertThat(shop.get("latitude").asDouble()).isEqualTo(lat);
        assertThat(shop.get("longitude").asDouble()).isEqualTo(lng);
        assertThat(shop.get("address").asText()).isEqualTo(address);
        assertThat(info.get("shops").get(0)).isEqualTo(shop);
    }

    private void assertSavedPoint(double lat, double lng, String address) throws Exception {
        Localisation loc = restaurants.findById(restaurantId).orElseThrow().getLocalisation();
        assertThat(loc.getLatitude()).isEqualTo(lat);
        assertThat(loc.getLongitude()).isEqualTo(lng);
        assertThat(loc.getAdresse()).isEqualTo(address);
        var response = get("/api/restaurants/" + restaurantId, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode publicLoc = json.readTree(response.body()).get("localisation");
        assertThat(publicLoc.get("latitude").asDouble()).isEqualTo(lat);
        assertThat(publicLoc.get("longitude").asDouble()).isEqualTo(lng);
    }

    private HttpResponse<String> update(Map<String, String> fields) throws Exception {
        Map<String, String> all = new LinkedHashMap<>(fields);
        all.put("nom", "GPS test");
        String boundary = "gps-test-boundary";
        StringBuilder body = new StringBuilder();
        all.forEach((key, value) -> body.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(key)
                .append("\"\r\n\r\n").append(value).append("\r\n"));
        body.append("--").append(boundary).append("--\r\n");
        return http.send(HttpRequest.newBuilder(uri("/api/restaurants/" + restaurantId))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private User user(UserRole role) {
        User u = new User();
        u.setEmail("gps-" + role + "-" + System.nanoTime() + "@mysugu.test");
        u.setNom("GPS"); u.setPrenom("Test"); u.setRole(role);
        u.setPassword("unused"); u.setIsActive(true);
        return users.save(u);
    }
}
