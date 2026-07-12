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
 * Contrat POST /api/v3/seller/balance-withdraw et POST
 * /api/v3/seller/close-withdraw-request : STUB bénin succès-no-op (pas de solde/retrait vendeur
 * natif — spec 3f §2/§3 GAP). Réponse 200 {@code {message}}, jamais 404/500, aucune persistance
 * (aucune entité "demande de retrait vendeur" n'existe côté natif pour ce cas). URL réelle
 * confirmée 3f.0 contre {@code app_constants.dart} : {@code cancelBalanceRequest =
 * '/api/v3/seller/close-withdraw-request'} — SANS suffixe {@code {id}}, contrairement à
 * {@code /withdraw/close-request/{id}} supposé par la conception initiale de la spec (déviation ;
 * cette route n'est d'ailleurs appelée par aucun écran actuel de Tiktak-vendor-app-moso — définie
 * mais inutilisée). Plan 3f.3.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerWithdrawStubTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "wstub-" + System.nanoTime() + "@test.mysugu";
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
    void balance_withdraw_returns_200_message_no_op() throws Exception {
        String token = token(ownerEmail);
        Resp r = post("/api/v3/seller/balance-withdraw",
                Map.of("amount", "100", "withdraw_method_id", "1"), token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.has("message")).isTrue();
        assertThat(n.get("message").asText()).isNotBlank();
    }

    @Test
    void close_withdraw_request_returns_200_message_no_op() throws Exception {
        String token = token(ownerEmail);
        Resp r = post("/api/v3/seller/close-withdraw-request", Map.of(), token);
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

    private record Resp(int status, String body) {}
}
