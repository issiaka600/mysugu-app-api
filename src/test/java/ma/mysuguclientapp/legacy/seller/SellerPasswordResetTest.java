package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.OtpResetSellerRepository;
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
 * Contrat OTP reset du shim vendeur : forgot-password -> verify-otp -> reset-password.
 * forgot crée une ligne OTP (2 min) ; verify l'accepte ; reset change le hash de sorte que
 * le login avec le nouveau mot de passe fonctionne.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerPasswordResetTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired OtpResetSellerRepository otpRepo;
    @Autowired PasswordEncoder encoder;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private final String ownerEmail = "seller-reset-" + System.nanoTime() + "@test.mysugu";

    @BeforeEach
    void seed() {
        User u = new User();
        u.setEmail(ownerEmail);
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("Reset"); u.setPrenom("Owner");
        u.setRole(UserRole.RESTAURANT_OWNER);
        u.setIsActive(true);
        userRepo.save(u);
    }

    @AfterEach
    void cleanup() {
        otpRepo.deleteByIdentity(ownerEmail);
        userRepo.findByEmail(ownerEmail).ifPresent(userRepo::delete);
    }

    @Test
    void otp_reset_flow_changes_password() throws Exception {
        assertThat(post("/api/v3/seller/auth/forgot-password", Map.of("identity", ownerEmail)).status)
                .isEqualTo(200);

        String code = otpRepo.findFirstByIdentityAndUsedAtIsNullOrderByCreatedAtDesc(ownerEmail)
                .orElseThrow().getCode();

        assertThat(post("/api/v3/seller/auth/verify-otp", Map.of("identity", ownerEmail, "otp", code)).status)
                .isEqualTo(200);

        assertThat(post("/api/v3/seller/auth/reset-password", Map.of(
                "_method", "put", "identity", ownerEmail, "otp", code,
                "password", "NewPass2026!", "confirm_password", "NewPass2026!")).status)
                .isEqualTo(200);

        // Login avec le nouveau mot de passe fonctionne, l'ancien échoue.
        Resp ok = post("/api/v3/seller/auth/login", Map.of("email", ownerEmail, "password", "NewPass2026!"));
        assertThat(ok.status).isEqualTo(200);
        JsonNode n = M.readTree(ok.body);
        assertThat(n.get("token").asText()).isNotBlank();

        assertThat(post("/api/v3/seller/auth/login", Map.of("email", ownerEmail, "password", "demo1234")).status)
                .isEqualTo(401);
    }

    @Test
    void forgot_unknown_identity_returns_403() throws Exception {
        assertThat(post("/api/v3/seller/auth/forgot-password",
                Map.of("identity", "nobody-" + System.nanoTime() + "@test.mysugu")).status)
                .isEqualTo(403);
    }

    private Resp post(String path, Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body)))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
