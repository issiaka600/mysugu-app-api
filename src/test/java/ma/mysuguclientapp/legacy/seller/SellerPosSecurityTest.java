package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sécurité POS du shim vendeur : tout /api/v3/seller/pos/** exige une authentification (couvert
 * par le matcher /api/v3/seller/** authenticated de la tranche 3a) et le rôle RESTAURANT_OWNER
 * (vérifié par SellerContext.requireOwner). Anonyme -> 401/403, CLIENT -> 403. Cf. plan 3g.6.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerPosSecurityTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private final String clientEmail = "pos-sec-client-" + System.nanoTime() + "@test.mysugu";
    private Long clientId;

    @BeforeEach
    void seed() {
        User client = new User();
        client.setEmail(clientEmail);
        client.setPassword(encoder.encode("demo1234"));
        client.setNom("C"); client.setPrenom("D");
        client.setRole(UserRole.CLIENT);
        client.setIsActive(true);
        clientId = userRepo.save(client).getId();
    }

    @AfterEach
    void cleanup() {
        userRepo.findById(clientId).ifPresent(userRepo::delete);
    }

    @Test
    void anonymous_pos_products_is_401_or_403() throws Exception {
        Resp r = get("/api/v3/seller/pos/products", null);
        assertThat(r.status).isIn(401, 403);
    }

    @Test
    void anonymous_pos_customers_is_401_or_403() throws Exception {
        Resp r = get("/api/v3/seller/pos/customers", null);
        assertThat(r.status).isIn(401, 403);
    }

    @Test
    void client_token_is_forbidden_via_requireOwner() throws Exception {
        String t = jwtTokenProvider.generateToken(userRepo.findById(clientId).orElseThrow());
        Resp r = get("/api/v3/seller/pos/product-list", t);
        assertThat(r.status).isEqualTo(403);
    }

    private Resp get(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
