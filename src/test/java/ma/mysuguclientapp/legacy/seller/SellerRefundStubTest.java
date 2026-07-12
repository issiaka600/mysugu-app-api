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
 * Contrat GET /api/v3/seller/refund/list, GET
 * /api/v3/seller/refund/refund-details?order_details_id=, POST
 * /api/v3/seller/refund/refund-status-update : STUB bénin — aucun domaine de remboursement natif
 * (le "refund" n'existe que dans les internals Stripe, spec 3f §2/§3 GAP). Jamais 404/500.
 * Réponse RÉELLE confirmée 3f.0 contre Tiktak-vendor-app-moso :
 * <ul>
 *   <li>{@code refund/list} : TABLEAU JSON NU (pas d'enveloppe — {@code refund_controller.dart}
 *   fait {@code apiResponse.response!.data.forEach((refund) => RefundModel.fromJson(refund))}),
 *   déviation vs l'enveloppe {@code {total_size, limit, offset, refunds:[...]}} supposée par la
 *   conception initiale de la spec.</li>
 *   <li>{@code refund/refund-details} : requête par QUERY PARAM {@code order_details_id}, PAS un
 *   path variable {@code {id}} ({@code refund_repository.dart::getRefundReqDetails}) ; réponse =
 *   OBJET JSON unique ({@code RefundDetailsModel.fromJson(apiResponse.response!.data)}).
 *   {@code RefundDetailsModel.fromJson} fait {@code json['<champ>'].toDouble()} SANS garde de
 *   nullité sur tous les champs numériques (product_price, product_total_discount,
 *   product_total_tax, subtotal, coupon_discount, refund_amount) -> ils ne doivent JAMAIS être
 *   {@code null} (défaut {@code 0}).</li>
 * </ul>
 * Plan 3f.4.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerRefundStubTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "refund-" + System.nanoTime() + "@test.mysugu";
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
    void refund_list_returns_empty_flat_array_never_errors() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/refund/list", token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.isArray()).as("body must be a raw JSON array, not an envelope object").isTrue();
        assertThat(n).isEmpty();
    }

    @Test
    void refund_details_returns_200_benign_object_never_404() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/refund/refund-details?order_details_id=1", token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.isObject()).isTrue();
        for (String field : new String[]{"product_price", "product_total_discount",
                "product_total_tax", "subtotal", "coupon_discount", "refund_amount"}) {
            assertThat(n.has(field)).as(field).isTrue();
            assertThat(n.get(field).isNull()).as(field + " must never be null").isFalse();
        }
    }

    @Test
    void refund_status_update_returns_200_message_no_op() throws Exception {
        String token = token(ownerEmail);
        Resp r = post("/api/v3/seller/refund/refund-status-update",
                Map.of("refund_status", "approved", "refund_request_id", "1", "note", "n"), token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.has("message")).isTrue();
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
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
