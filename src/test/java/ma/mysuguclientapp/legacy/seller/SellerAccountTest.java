package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.DeviceTokenRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat compte/appareil du shim vendeur : cm-firebase-token persiste un DeviceToken pour le
 * vendeur ; language-change met à jour l'utilisateur ; account-delete désactive le compte et
 * renvoie la forme de succès 6valley. Toutes exigent un JWT RESTAURANT_OWNER.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerAccountTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired DeviceTokenRepository deviceTokenRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired TransactionTemplate tx;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private final String ownerEmail = "seller-account-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId;

    @BeforeEach
    void seed() {
        User u = new User();
        u.setEmail(ownerEmail);
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("Acc"); u.setPrenom("Owner");
        u.setRole(UserRole.RESTAURANT_OWNER);
        u.setIsActive(true);
        ownerId = userRepo.save(u).getId();
    }

    @AfterEach
    void cleanup() {
        tx.executeWithoutResult(s -> {
            // Supprime d'abord les device tokens (FK vers users), puis le compte.
            deviceTokenRepo.findByUserIdAndIsActiveTrue(ownerId).forEach(deviceTokenRepo::delete);
            userRepo.findById(ownerId).ifPresent(userRepo::delete);
        });
    }

    private String token() throws Exception {
        Resp r = post("/api/v3/seller/auth/login", Map.of("email", ownerEmail, "password", "demo1234"), null);
        return M.readTree(r.body).get("token").asText();
    }

    @Test
    void cm_firebase_token_persists_device_token() throws Exception {
        String t = token();
        Resp r = post("/api/v3/seller/cm-firebase-token",
                Map.of("_method", "put", "cm_firebase_token", "fcm-token-abc"), t);
        assertThat(r.status).isEqualTo(200);
        assertThat(deviceTokenRepo.findByUserIdAndIsActiveTrue(ownerId)).isNotEmpty();
    }

    @Test
    void language_change_updates_user() throws Exception {
        String t = token();
        Resp r = post("/api/v3/seller/language-change",
                Map.of("_method", "put", "current_language", "fr"), t);
        assertThat(r.status).isEqualTo(200);
        assertThat(userRepo.findById(ownerId).orElseThrow().getAppLanguage()).isEqualTo("fr");
    }

    @Test
    void account_delete_deactivates_account() throws Exception {
        String t = token();
        Resp r = get("/api/v3/seller/account-delete", t);
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).get("message").asText()).isNotBlank();
        User after = userRepo.findById(ownerId).orElseThrow();
        assertThat(after.getIsActive()).isFalse();
        assertThat(after.getIsDeleted()).isTrue();
    }

    @Test
    void account_endpoints_require_auth() throws Exception {
        assertThat(get("/api/v3/seller/account-delete", null).status).isIn(401, 403);
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
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
