package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
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
 * Contrat GET /api/v3/seller/top-delivery-man : DECISION 3j.3 = STUB bénin (liste vide), pas de
 * réutilisation de {@code StatistiquesService.getTopLivreurs} (admin-global, non scopé par
 * restaurant — voir commentaire du contrôleur pour la justification complète). Enveloppe
 * confirmée via Tiktak-vendor-app-moso (TopDeliveryManModel.fromJson lit
 * {@code total_size/limit/offset/delivery_man}, PAS un tableau nu comme supposé initialement par
 * la spec — correction 3j.0). Jamais 500. Plan 3j.3.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerTopDeliveryManTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "top-dm-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId;

    @BeforeEach
    void seed() {
        User owner = new User();
        owner.setEmail(ownerEmail);
        owner.setPassword(encoder.encode("demo1234"));
        owner.setNom("N"); owner.setPrenom("P");
        owner.setRole(UserRole.RESTAURANT_OWNER);
        owner.setIsActive(true);
        ownerId = userRepo.save(owner).getId();
    }

    @AfterEach
    void cleanup() {
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    @Test
    void returns_200_benign_envelope_never_crashes() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/top-delivery-man", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.has("total_size")).isTrue();
        assertThat(n.has("limit")).isTrue();
        assertThat(n.has("offset")).isTrue();
        assertThat(n.has("delivery_man")).isTrue();
        assertThat(n.get("delivery_man").isArray()).isTrue();
        assertThat(n.get("delivery_man")).isEmpty();
        assertThat(n.get("total_size").asInt()).isEqualTo(0);
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
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
