package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat shop-info du shim vendeur : GET /api/v3/seller/shop-info renvoie l'objet "shop"
 * 6valley mappé depuis le Restaurant du vendeur (RestaurantService.getMonRestaurant).
 * temporary_close = !isActive ; champs non mappés présents avec valeurs bénignes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class ShopInfoTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "shop-info-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId;
    private Long restoId;

    @BeforeEach
    void seed() {
        User owner = new User();
        owner.setEmail(ownerEmail);
        owner.setPassword(encoder.encode("demo1234"));
        owner.setNom("Bar"); owner.setPrenom("Foo");
        owner.setRole(UserRole.RESTAURANT_OWNER);
        owner.setIsActive(true);
        owner = userRepo.save(owner);
        ownerId = owner.getId();

        Restaurant r = new Restaurant();
        r.setNom("Chez Foo");
        r.setDescription("desc");
        r.setLogoUrl("restaurants/logos/foo.png");
        r.setBannerUrl("restaurants/banners/foo.png");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(owner);
        r.setIsActive(true);
        r.setLocalisation(new Localisation(1.0, 2.0, "1 rue Test", "Casablanca", "20000", "Maroc"));
        restoId = restoRepo.save(r).getId();
    }

    @AfterEach
    void cleanup() {
        restoRepo.findById(restoId).ifPresent(restoRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    @Test
    void shop_info_returns_6valley_shop_object() throws Exception {
        String t = ownerToken();
        Resp r = get("/api/v3/seller/shop-info", t);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("name").asText()).isEqualTo("Chez Foo");
        assertThat(n.get("temporary_close").asBoolean()).isFalse();
        // image mappé depuis logoUrl (URL publique dérivée de l'object name par le service natif).
        assertThat(n.get("image").asText()).contains("restaurants/logos/foo.png");
        assertThat(n.get("banner").asText()).contains("restaurants/banners/foo.png");
        // Champs 6valley non mappés -> présents, valeurs bénignes (jamais absents/null-crash).
        assertThat(n.get("minimum_order_amount").asInt()).isEqualTo(0);
        assertThat(n.get("vacation_status").asBoolean()).isFalse();
        assertThat(n.has("rating")).isTrue();
        assertThat(n.has("delivery_charge")).isTrue();

        Resp alias = get("/api/v3/seller/shop", t);
        assertThat(alias.status).isEqualTo(200);
        assertThat(M.readTree(alias.body).get("banner").asText()).contains("restaurants/banners/foo.png");
    }

    private String ownerToken() throws Exception {
        Resp r = post("/api/v3/seller/auth/login", Map.of("email", ownerEmail, "password", "demo1234"));
        return M.readTree(r.body).get("token").asText();
    }

    private Resp post(String path, Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body))).build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp get(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
