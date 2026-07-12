package ma.mysuguclientapp.legacy.seller.deliveryman;

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
 * Task 3e.5: STUB lists (reviews/order-status-history/collect-cash-list/withdraw list+details) +
 * withdraw/status-update (STUB — vendor CANNOT approve a retrait, admin-only). Every list -> 200
 * benign envelope, never 404/500. Withdraw approval is a success-no-op — never touches
 * DemandeRetrait.
 * // GAP: vendor->livreur ownership not native (umbrella §4).
 * Plan 2026-07-10-vendor-3e-deliveryman.md task 3e.5.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerDeliveryManStubListTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "dm-stub-list-owner-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, livreurId;

    @BeforeEach
    void seed() {
        User owner = new User();
        owner.setEmail(ownerEmail);
        owner.setPassword(encoder.encode("demo1234"));
        owner.setNom("N"); owner.setPrenom("P");
        owner.setRole(UserRole.RESTAURANT_OWNER);
        owner.setIsActive(true);
        ownerId = userRepo.save(owner).getId();

        User livreur = new User();
        livreur.setEmail("dm-stub-list-livreur-" + System.nanoTime() + "@test.mysugu");
        livreur.setPassword(encoder.encode("demo1234"));
        livreur.setNom("N"); livreur.setPrenom("P");
        livreur.setRole(UserRole.LIVREUR);
        livreur.setIsActive(true);
        livreurId = userRepo.save(livreur).getId();
    }

    @AfterEach
    void cleanup() {
        userRepo.findById(livreurId).ifPresent(userRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    @Test
    void reviews_returns_200_benign_envelope_never_404() throws Exception {
        String token = token();
        Resp r = get("/api/v3/seller/delivery-man/reviews/" + livreurId, token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.has("total_size")).isTrue();
        assertThat(n.get("reviews").isArray()).isTrue();
        assertThat(n.get("reviews")).isEmpty();
    }

    @Test
    void order_status_history_returns_bare_empty_array_never_404() throws Exception {
        String token = token();
        Resp r = get("/api/v3/seller/delivery-man/order-status-history/" + livreurId, token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.isArray()).isTrue();
        assertThat(n).isEmpty();
    }

    @Test
    void collect_cash_list_returns_200_benign_envelope_never_404() throws Exception {
        String token = token();
        Resp r = get("/api/v3/seller/delivery-man/collect-cash-list/" + livreurId, token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("collected_cash").isArray()).isTrue();
        assertThat(n.get("collected_cash")).isEmpty();
    }

    @Test
    void withdraw_list_returns_200_benign_envelope_never_404() throws Exception {
        String token = token();
        Resp r = get("/api/v3/seller/delivery-man/withdraw/list", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("withdraws").isArray()).isTrue();
        assertThat(n.get("withdraws")).isEmpty();
        assertThat(n.get("total_size").asInt()).isEqualTo(0);
    }

    @Test
    void withdraw_details_returns_200_neutral_never_404() throws Exception {
        String token = token();
        Resp r = get("/api/v3/seller/delivery-man/withdraw/details/999999", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.has("details")).isTrue();
    }

    @Test
    void withdraw_status_update_returns_200_message_no_op_vendor_cannot_approve() throws Exception {
        String token = token();
        Resp r = post("/api/v3/seller/delivery-man/withdraw/status-update",
                Map.of("_method", "put", "id", 999999, "note", "ok", "approved", 1), token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.has("message")).isTrue();
        assertThat(n.get("message").asText()).isNotBlank();
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
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
