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
 * Contrat GET/POST/DELETE /api/v3/seller/shipping-method/* : STUB bénin intégral — mysugu n'a
 * aucune méthode d'expédition propre au vendeur (meal delivery tarifé par distance via
 * ZoneDeploiement, pas de "Standard/Express" par vendeur). Jamais 404/500. Réponses confirmées
 * 3i.0 contre Tiktak-vendor-app-moso (shipping_controller.dart / shipping_model.dart) :
 * <ul>
 *   <li>{@code list} : TABLEAU JSON NU (pas d'enveloppe) — {@code getShippingList} fait
 *   {@code apiResponse.response!.data.forEach(...)}. Liste vide -> jamais de parsing de
 *   {@code ShippingModel.fromJson} (qui exige {@code cost} non-null et {@code status} booléen
 *   réel).</li>
 *   <li>{@code edit} : objet bénin, {@code status} est un BOOLÉEN JSON (pas 0/1) et {@code cost}
 *   non-null (chemin défini dans app_constants.dart mais jamais réellement invoqué par le
 *   repository -- fourni quand même pour ne jamais 404).</li>
 *   <li>{@code add/update/delete/status} : accusé bénin {@code {"message": ...}}, no-op (rien
 *   n'est persisté).</li>
 * </ul>
 * Plan 3i.1.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerShippingMethodStubTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "shipmethod-" + System.nanoTime() + "@test.mysugu";
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
    void list_returns_empty_flat_array_never_errors() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/shipping-method/list", token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.isArray()).as("body must be a raw JSON array, not an envelope object").isTrue();
        assertThat(n).isEmpty();
    }

    @Test
    void add_returns_200_message_no_op() throws Exception {
        String token = token(ownerEmail);
        Resp r = post("/api/v3/seller/shipping-method/add",
                Map.of("title", "Standard", "duration", "2-3 days", "cost", 5), token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.has("message")).isTrue();
    }

    @Test
    void update_returns_200_message_no_op() throws Exception {
        String token = token(ownerEmail);
        Resp r = post("/api/v3/seller/shipping-method/update/5",
                Map.of("_method", "put", "title", "Standard", "duration", "2-3 days", "cost", 5), token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.has("message")).isTrue();
    }

    @Test
    void edit_returns_200_benign_object_with_boolean_status() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/shipping-method/edit", token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.isObject()).isTrue();
        assertThat(n.has("status")).isTrue();
        assertThat(n.get("status").isBoolean()).as("status must be a real JSON boolean").isTrue();
        assertThat(n.get("status").asBoolean()).isFalse();
        assertThat(n.has("cost")).isTrue();
        assertThat(n.get("cost").isNull()).as("cost must never be null").isFalse();
    }

    @Test
    void delete_returns_200_message_no_op() throws Exception {
        String token = token(ownerEmail);
        Resp r = delete("/api/v3/seller/shipping-method/delete/5", token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.has("message")).isTrue();
    }

    @Test
    void status_returns_200_message_no_op() throws Exception {
        String token = token(ownerEmail);
        Resp r = post("/api/v3/seller/shipping-method/status",
                Map.of("_method", "put", "id", 5, "status", 0), token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.has("message")).isTrue();
    }

    @Test
    void anonymous_list_is_401_or_403() throws Exception {
        Resp r = get("/api/v3/seller/shipping-method/list", null);
        assertThat(r.status).isIn(401, 403);
    }

    private String token(String email) throws Exception {
        Resp r = postNoAuth("/api/v3/seller/auth/login", Map.of("email", email, "password", "demo1234"));
        return M.readTree(r.body).get("token").asText();
    }

    private Resp postNoAuth(String path, Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body))).build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp post(String path, Object body, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
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

    private Resp delete(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .DELETE().build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
