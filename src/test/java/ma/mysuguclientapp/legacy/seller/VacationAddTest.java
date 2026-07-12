package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.RestaurantRepository;
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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat vacation-add du shim vendeur (GAP mappé sur isActive + note) : POST
 * /api/v3/seller/vacation-add {vacation_start_date,vacation_end_date,vacation_note,vacation_status}
 * passe la boutique en congés (isActive=false), persiste la note et renvoie un écho 6valley valide.
 * GET /shop-info reflète l'écho vacances + temporary_close. Jamais 404/500.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class VacationAddTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PasswordEncoder encoder;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private final String ownerEmail = "vacation-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, restoId;

    @BeforeEach
    void seed() {
        User owner = new User();
        owner.setEmail(ownerEmail);
        owner.setPassword(encoder.encode("demo1234"));
        owner.setNom("N"); owner.setPrenom("P");
        owner.setRole(UserRole.RESTAURANT_OWNER);
        owner.setIsActive(true);
        owner = userRepo.save(owner);
        ownerId = owner.getId();

        Restaurant r = new Restaurant();
        r.setNom("Boutique");
        r.setDescription("desc");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(owner);
        r.setIsActive(true);
        r.setLocalisation(new Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        restoId = restoRepo.save(r).getId();
    }

    @AfterEach
    void cleanup() {
        restoRepo.findById(restoId).ifPresent(restoRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    @Test
    void vacation_add_echoes_and_sets_isActive_false() throws Exception {
        String t = token();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("_method", "put");
        body.put("vacation_status", 1);
        body.put("vacation_start_date", "2026-08-01");
        body.put("vacation_end_date", "2026-08-10");
        body.put("vacation_note", "Conges annuels");

        Resp r = post("/api/v3/seller/vacation-add", body, t);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.get("vacation_status").asBoolean()).isTrue();
        assertThat(n.get("vacation_start_date").asText()).isEqualTo("2026-08-01");
        assertThat(n.get("vacation_end_date").asText()).isEqualTo("2026-08-10");
        assertThat(n.get("vacation_note").asText()).isEqualTo("Conges annuels");

        assertThat(restoRepo.findById(restoId).orElseThrow().getIsActive()).isFalse();

        Resp info = get("/api/v3/seller/shop-info", t);
        JsonNode s = M.readTree(info.body);
        assertThat(s.get("temporary_close").asBoolean()).isTrue();
        assertThat(s.get("vacation_status").asBoolean()).isTrue();
        assertThat(s.get("vacation_start_date").asText()).isEqualTo("2026-08-01");
        assertThat(s.get("vacation_end_date").asText()).isEqualTo("2026-08-10");
    }

    private String token() throws Exception {
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

    private record Resp(int status, String body) {}
}
