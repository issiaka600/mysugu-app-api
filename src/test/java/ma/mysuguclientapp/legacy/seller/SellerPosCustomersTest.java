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
 * Contrat POS customers du shim vendeur : GET /api/v3/seller/pos/customers -> enveloppe bénigne
 * vide (GAP — mysugu n'a pas de liste de clients propre au vendeur). Jamais 404. Cf. plan 3g.3.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerPosCustomersTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "pos-cust-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, restoId;

    @BeforeEach
    void seed() {
        ownerId = newOwner(ownerEmail);
        restoId = newResto("Boutique Cust", ownerId);
    }

    @AfterEach
    void cleanup() {
        restoRepo.findById(restoId).ifPresent(restoRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    @Test
    void customers_returns_empty_envelope_never_404() throws Exception {
        String t = token(ownerEmail);
        Resp r = get("/api/v3/seller/pos/customers?type=all", t);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("customers").isArray()).isTrue();
        assertThat(n.get("customers")).isEmpty();
        assertThat(n.get("total_size").asInt()).isEqualTo(0);
    }

    private Long newOwner(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("N"); u.setPrenom("P");
        u.setRole(UserRole.RESTAURANT_OWNER);
        u.setIsActive(true);
        return userRepo.save(u).getId();
    }

    private Long newResto(String nom, Long ownerId) {
        Restaurant r = new Restaurant();
        r.setNom(nom);
        r.setDescription("desc");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(userRepo.findById(ownerId).orElseThrow());
        r.setIsActive(true);
        r.setLocalisation(new Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        return restoRepo.save(r).getId();
    }

    private String token(String email) throws Exception {
        Resp r = post("/api/v3/seller/auth/login", Map.of("email", email, "password", "demo1234"));
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
