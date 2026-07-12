package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.ModeVersementRestaurant;
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
 * Contrat GET /api/v3/seller/withdraw-method-list : STUB bénin, une ligne par valeur de l'enum
 * natif {@link ModeVersementRestaurant} (pas de méthodes de retrait configurables réellement —
 * spec 3f §3 GAP). Réponse = TABLEAU JSON NU (confirmé 3f.0 :
 * {@code wallet_controller.dart::getWithdrawMethods} fait
 * {@code response.response!.data.forEach((method) => methodList.add(WithdrawModel.fromJson(method)))}).
 * {@code WithdrawModel.fromJson} fait {@code json['is_active'] ? 1 : 0} SANS garde de nullité ->
 * {@code is_active} doit toujours être un booléen JSON non nul. Jamais 500. Plan 3f.2.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerWithdrawMethodsTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "wm-" + System.nanoTime() + "@test.mysugu";
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
    void returns_flat_array_one_row_per_mode_versement_value() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/withdraw-method-list", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.isArray()).as("body must be a raw JSON array, not an envelope object").isTrue();
        assertThat(n).hasSize(ModeVersementRestaurant.values().length);

        boolean foundVirement = false;
        for (JsonNode row : n) {
            assertThat(row.has("id")).isTrue();
            assertThat(row.has("method_name")).isTrue();
            assertThat(row.get("method_fields").isArray()).isTrue();
            assertThat(row.get("is_active").isBoolean())
                    .as("is_active must be a JSON boolean (Dart does json['is_active'] ? 1 : 0 without a null guard)")
                    .isTrue();
            if (row.get("method_name").asText().contains("VIREMENT_BANCAIRE")) {
                foundVirement = true;
            }
        }
        assertThat(foundVirement).isTrue();
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
