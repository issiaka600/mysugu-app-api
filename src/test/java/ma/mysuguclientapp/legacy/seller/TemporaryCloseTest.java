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
 * Contrat temporary-close du shim vendeur (GAP mappé sur Restaurant.isActive) : POST
 * /api/v3/seller/temporary-close {status} passe la boutique en fermeture temporaire
 * (status=1 -> temporary_close=true -> isActive=false). Enveloppe 6valley bénigne, jamais 404/500.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class TemporaryCloseTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "temp-close-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, restoId;

    @BeforeEach
    void seed() {
        User owner = new User();
        owner.setEmail(ownerEmail);
        owner.setPassword(encoder.encode("demo1234"));
        owner.setNom("N"); owner.setPrenom("P");
        owner.setRole(UserRole.RESTAURANT_OWNER);
        owner.setIsActive(true);
        owner = userRepo.save(owner);
        ownerId = owner.getId();

        Restaurant r = new Restaurant();
        r.setNom("Boutique");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(owner);
        r.setIsActive(true);
        r.setLocalisation(new Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        restoId = restoRepo.save(r).getId();
    }

    @AfterEach
    void cleanup() {
        restoRepo.findById(restoId).ifPresent(restoRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    @Test
    void temporary_close_sets_isActive_false() throws Exception {
        String t = token();
        Resp r = post("/api/v3/seller/temporary-close", Map.of("_method", "put", "status", 1), t);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.get("message").asText()).isNotBlank();
        assertThat(n.get("temporary_close").asBoolean()).isTrue();

        assertThat(restoRepo.findById(restoId).orElseThrow().getIsActive()).isFalse();
        Resp info = get("/api/v3/seller/shop-info", t);
        assertThat(M.readTree(info.body).get("temporary_close").asBoolean()).isTrue();
    }

    @Test
    void temporary_close_reopen_sets_isActive_true() throws Exception {
        String t = token();
        post("/api/v3/seller/temporary-close", Map.of("_method", "put", "status", 1), t);
        Resp r = post("/api/v3/seller/temporary-close", Map.of("_method", "put", "status", 0), t);
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).get("temporary_close").asBoolean()).isFalse();
        assertThat(restoRepo.findById(restoId).orElseThrow().getIsActive()).isTrue();
    }

    private String token() throws Exception {
        Resp r = post("/api/v3/seller/auth/login", Map.of("email", ownerEmail, "password", "demo1234"), null);
        return M.readTree(r.body).get("token").asText();
    }

    private Resp post(String path, Object body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body)));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
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
