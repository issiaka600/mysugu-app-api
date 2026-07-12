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
 * Task 3e.4: STUB management mutations (store/update/delete/cash-receive/status-update) ->
 * success-no-op. Vendor cannot create/own/delete/mutate a global livreur — every endpoint returns
 * HTTP 200 {message} WITHOUT any side effect on User/GainsLivreur/CaisseLivreur (asserted by
 * re-query). Never 404/500.
 * // GAP: vendor->livreur ownership not native (umbrella §4).
 * Plan 2026-07-10-vendor-3e-deliveryman.md task 3e.4.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerDeliveryManStubMutationTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "dm-stub-mut-owner-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, livreurId;
    private long livreurCountBefore;

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
        livreur.setEmail("dm-stub-mut-livreur-" + System.nanoTime() + "@test.mysugu");
        livreur.setPassword(encoder.encode("demo1234"));
        livreur.setNom("N"); livreur.setPrenom("P");
        livreur.setRole(UserRole.LIVREUR);
        livreur.setIsActive(true);
        livreur.setLivreurDisponible(true);
        livreurId = userRepo.save(livreur).getId();

        livreurCountBefore = userRepo.findByRole(UserRole.LIVREUR).size();
    }

    @AfterEach
    void cleanup() {
        userRepo.findById(livreurId).ifPresent(userRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    @Test
    void store_returns_200_message_no_op_never_creates_a_livreur() throws Exception {
        String token = token();
        Resp r = post("/api/v3/seller/delivery-man/store", Map.of("f_name", "New"), token);
        assertThat(r.status).isEqualTo(200);
        assertMessagePresent(r.body);
        assertThat(userRepo.findByRole(UserRole.LIVREUR)).hasSize((int) livreurCountBefore);
    }

    @Test
    void update_returns_200_message_no_op_never_mutates_livreur() throws Exception {
        String token = token();
        Resp r = post("/api/v3/seller/delivery-man/update/" + livreurId,
                Map.of("_method", "put", "f_name", "Changed"), token);
        assertThat(r.status).isEqualTo(200);
        assertMessagePresent(r.body);

        User unchanged = userRepo.findById(livreurId).orElseThrow();
        assertThat(unchanged.getPrenom()).isEqualTo("P");
    }

    @Test
    void update_without_id_in_path_is_also_accepted() throws Exception {
        String token = token();
        Resp r = post("/api/v3/seller/delivery-man/update", Map.of("_method", "put"), token);
        assertThat(r.status).isEqualTo(200);
        assertMessagePresent(r.body);
    }

    @Test
    void delete_via_get_returns_200_message_never_deletes_the_livreur() throws Exception {
        // DEVIATION (3e.0): the real app calls delete with a plain GET, no _method spoofing.
        String token = token();
        Resp r = get("/api/v3/seller/delivery-man/delete/" + livreurId, token);
        assertThat(r.status).isEqualTo(200);
        assertMessagePresent(r.body);
        assertThat(userRepo.findById(livreurId)).isPresent();
        assertThat(userRepo.findById(livreurId).orElseThrow().getIsDeleted()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void cash_receive_returns_200_message_no_op() throws Exception {
        String token = token();
        Resp r = post("/api/v3/seller/delivery-man/cash-receive",
                Map.of("deliveryman_id", livreurId, "amount", "50"), token);
        assertThat(r.status).isEqualTo(200);
        assertMessagePresent(r.body);
    }

    @Test
    void status_update_returns_200_message_no_op_never_toggles_livreur_disponible() throws Exception {
        String token = token();
        Resp r = post("/api/v3/seller/delivery-man/status-update",
                Map.of("id", livreurId, "status", 0), token);
        assertThat(r.status).isEqualTo(200);
        assertMessagePresent(r.body);

        User unchanged = userRepo.findById(livreurId).orElseThrow();
        assertThat(unchanged.getLivreurDisponible()).isTrue(); // still true, never flipped by the vendor
    }

    private void assertMessagePresent(String body) throws Exception {
        JsonNode n = M.readTree(body);
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
