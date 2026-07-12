package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.EmailService;
import ma.mysuguclientapp.services.implementations.MinioService;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.junit.jupiter.api.AfterEach;
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
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat registration du shim vendeur : POST /api/v3/seller/registration (multipart) crée un
 * compte RESTAURANT_OWNER et renvoie {token}. Un email déjà utilisé -> 403 {errors:[...]}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerRegistrationTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PasswordEncoder encoder;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private final String email = "seller-reg-" + System.nanoTime() + "@test.mysugu";

    @AfterEach
    void cleanup() {
        userRepo.findByEmail(email).ifPresent(owner -> {
            restoRepo.findByOwnerId(owner.getId()).ifPresent(restoRepo::delete);
            userRepo.delete(owner);
        });
    }

    @Test
    void registration_creates_owner_and_returns_token() throws Exception {
        Resp r = postMultipart(email);
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).get("token").asText()).isNotBlank();

        User created = userRepo.findByEmail(email).orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.RESTAURANT_OWNER);

        // Gap 3a fermé : la boutique est créée à l'inscription à partir des champs shop_*,
        // pour que currentRestaurant résolve immédiatement (statut EN_ATTENTE, non actif).
        assertThat(restoRepo.findByOwnerId(created.getId())).isPresent();
        assertThat(restoRepo.findByOwnerId(created.getId()).orElseThrow().getNom()).isEqualTo("Chez Foo");
    }

    @Test
    void duplicate_email_returns_403_errors() throws Exception {
        User existing = new User();
        existing.setEmail(email);
        existing.setPassword(encoder.encode("demo1234"));
        existing.setNom("X"); existing.setPrenom("Y");
        existing.setRole(UserRole.RESTAURANT_OWNER);
        existing.setIsActive(true);
        userRepo.save(existing);

        Resp r = postMultipart(email);
        assertThat(r.status).isEqualTo(403);
        JsonNode errors = M.readTree(r.body).get("errors");
        assertThat(errors.isArray()).isTrue();
        assertThat(errors.get(0).get("message").asText()).isNotBlank();
    }

    private Resp postMultipart(String email) throws Exception {
        String boundary = "----sellerregboundary";
        String CRLF = "\r\n";
        StringBuilder sb = new StringBuilder();
        String[][] fields = {
                {"f_name", "Foo"}, {"l_name", "Bar"}, {"phone", "+212600009999"},
                {"email", email}, {"password", "demo1234"}, {"confirm_password", "demo1234"},
                {"shop_name", "Chez Foo"}, {"shop_address", "1 rue Test"}
        };
        for (String[] f : fields) {
            sb.append("--").append(boundary).append(CRLF)
              .append("Content-Disposition: form-data; name=\"").append(f[0]).append("\"").append(CRLF)
              .append(CRLF).append(f[1]).append(CRLF);
        }
        sb.append("--").append(boundary).append("--").append(CRLF);

        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v3/seller/registration"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
