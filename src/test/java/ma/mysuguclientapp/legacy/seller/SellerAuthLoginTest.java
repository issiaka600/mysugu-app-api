package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.EmailService;
import ma.mysuguclientapp.services.implementations.MinioService;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat login du shim vendeur : POST /api/v3/seller/auth/login {email,password}
 * -> 200 {token} pour un RESTAURANT_OWNER ; 401 {errors:[...]} sinon (mauvais mot de
 * passe, ou compte non-vendeur type CLIENT).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerAuthLoginTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private final String ownerEmail = "seller-login-owner-" + System.nanoTime() + "@test.mysugu";
    private final String clientEmail = "seller-login-client-" + System.nanoTime() + "@test.mysugu";

    @BeforeEach
    void seed() {
        userRepo.save(newUser(ownerEmail, UserRole.RESTAURANT_OWNER));
        userRepo.save(newUser(clientEmail, UserRole.CLIENT));
    }

    @AfterEach
    void cleanup() {
        userRepo.findByEmail(ownerEmail).ifPresent(userRepo::delete);
        userRepo.findByEmail(clientEmail).ifPresent(userRepo::delete);
    }

    @Test
    void owner_login_returns_token() throws Exception {
        Resp r = post(Map.of("email", ownerEmail, "password", "demo1234"));
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).get("token").asText()).isNotBlank();
    }

    @Test
    void wrong_password_returns_401_errors() throws Exception {
        Resp r = post(Map.of("email", ownerEmail, "password", "wrong"));
        assertThat(r.status).isEqualTo(401);
        JsonNode errors = M.readTree(r.body).get("errors");
        assertThat(errors.isArray()).isTrue();
        assertThat(errors.get(0).get("message").asText()).isNotBlank();
    }

    @Test
    void non_owner_login_is_rejected() throws Exception {
        Resp r = post(Map.of("email", clientEmail, "password", "demo1234"));
        assertThat(r.status).isEqualTo(401);
        assertThat(M.readTree(r.body).get("errors").isArray()).isTrue();
    }

    private Resp post(Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v3/seller/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body)))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private User newUser(String email, UserRole role) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("Test");
        u.setPrenom("Seller");
        u.setRole(role);
        u.setIsActive(true);
        return u;
    }

    private record Resp(int status, String body) {}
}
