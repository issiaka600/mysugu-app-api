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
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat seller-update du shim vendeur : POST /api/v3/seller/seller-update (multipart,
 * _method:put toléré) modifie le profil du RESTAURANT_OWNER via UserService.updateProfile,
 * renvoie l'objet seller à jour, et un GET /seller-info reflète les nouvelles valeurs.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerUpdateTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private final String ownerEmail = "seller-update-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId;

    @BeforeEach
    void seed() {
        User owner = new User();
        owner.setEmail(ownerEmail);
        owner.setPassword(encoder.encode("demo1234"));
        owner.setNom("Bar"); owner.setPrenom("Foo");
        owner.setTelephone("+212600001111");
        owner.setRole(UserRole.RESTAURANT_OWNER);
        owner.setIsActive(true);
        ownerId = userRepo.save(owner).getId();
    }

    @AfterEach
    void cleanup() {
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    @Test
    void seller_update_changes_profile_and_persists() throws Exception {
        String t = ownerToken();
        Resp r = postMultipart("/api/v3/seller/seller-update", t, new String[][]{
                {"_method", "put"}, {"f_name", "Nouveau"}, {"l_name", "Nom"}, {"phone", "+212611112222"}
        });
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.get("f_name").asText()).isEqualTo("Nouveau");
        assertThat(n.get("phone").asText()).isEqualTo("+212611112222");

        Resp info = get("/api/v3/seller/seller-info", t);
        JsonNode after = M.readTree(info.body);
        assertThat(after.get("f_name").asText()).isEqualTo("Nouveau");
        assertThat(after.get("l_name").asText()).isEqualTo("Nom");
        assertThat(after.get("phone").asText()).isEqualTo("+212611112222");
    }

    private String ownerToken() throws Exception {
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
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp postMultipart(String path, String token, String[][] fields) throws Exception {
        String boundary = "----sellerupdateboundary";
        String CRLF = "\r\n";
        StringBuilder sb = new StringBuilder();
        for (String[] f : fields) {
            sb.append("--").append(boundary).append(CRLF)
              .append("Content-Disposition: form-data; name=\"").append(f[0]).append("\"").append(CRLF)
              .append(CRLF).append(f[1]).append(CRLF);
        }
        sb.append("--").append(boundary).append("--").append(CRLF);

        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
