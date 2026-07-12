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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat GET/POST /api/v3/seller/shipping/* : STUB bénin intégral — mysugu n'a ni coût
 * d'expédition par catégorie ni type d'expédition sélectionnable par le vendeur (meal delivery
 * tarifé par distance via ZoneDeploiement, admin, hors-scope de ce shim). Jamais 404/500.
 * Réponses confirmées 3i.0 contre Tiktak-vendor-app-moso (shipping_controller.dart /
 * category_wise_shipping_model.dart) :
 * <ul>
 *   <li>{@code all-category-cost} : la clé {@code all_category_shipping_cost} DOIT exister en
 *   tant que liste (même vide) — {@code getCategoryWiseShippingMethod} fait
 *   {@code CategoryWiseShippingModel.fromJson(data).allCategoryShippingCost!} (force-unwrap) et
 *   {@code data['all_category_shipping_cost'].forEach(...)}.</li>
 *   <li>{@code get-shipping-method} : la clé {@code type} DOIT exister —
 *   {@code getSelectedShippingMethodType} lit {@code data['type']}.</li>
 *   <li>{@code set-category-cost}/{@code selected-shipping-method} : accusé bénin no-op, l'app
 *   ne vérifie que le status 200.</li>
 * </ul>
 * Plan 3i.2.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerShippingStubTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "shipping-" + System.nanoTime() + "@test.mysugu";
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
    void all_category_cost_returns_key_with_empty_array_never_404() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/shipping/all-category-cost", token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.isObject()).isTrue();
        assertThat(n.has("all_category_shipping_cost")).as("force-unwrapped by the app").isTrue();
        assertThat(n.get("all_category_shipping_cost").isArray()).isTrue();
        assertThat(n.get("all_category_shipping_cost")).isEmpty();
    }

    @Test
    void set_category_cost_returns_200_message_no_op() throws Exception {
        String token = token(ownerEmail);
        Resp r = post("/api/v3/seller/shipping/set-category-cost",
                Map.of("ids", List.of(1), "cost", List.of(2.5), "multiply_qty", List.of(0)), token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.has("message")).isTrue();
    }

    @Test
    void selected_shipping_method_returns_200() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/shipping/selected-shipping-method?shipping_type=order_wise", token);
        assertThat(r.status).isEqualTo(200);
    }

    @Test
    void get_shipping_method_returns_type_key_never_404() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/shipping/get-shipping-method", token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.isObject()).isTrue();
        assertThat(n.has("type")).as("app reads data['type']").isTrue();
        assertThat(n.get("type").asText()).isEqualTo("order_wise");
    }

    @Test
    void anonymous_all_category_cost_is_401_or_403() throws Exception {
        Resp r = get("/api/v3/seller/shipping/all-category-cost", null);
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

    private record Resp(int status, String body) {}
}
