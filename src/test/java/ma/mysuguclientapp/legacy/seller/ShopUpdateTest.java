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
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat shop-update du shim vendeur : POST /api/v3/seller/shop-update (multipart, _method:put
 * toléré) met à jour le Restaurant du vendeur via RestaurantService.updateRestaurant. L'id est
 * résolu côté serveur via SellerContext.currentRestaurant(email) — jamais depuis le corps — donc
 * un vendeur ne peut pas muter la boutique d'un autre.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class ShopUpdateTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PasswordEncoder encoder;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private final String owner1Email = "shop-upd1-" + System.nanoTime() + "@test.mysugu";
    private final String owner2Email = "shop-upd2-" + System.nanoTime() + "@test.mysugu";
    private Long owner1Id, owner2Id, resto1Id, resto2Id;

    @BeforeEach
    void seed() {
        owner1Id = newOwner(owner1Email);
        owner2Id = newOwner(owner2Email);
        resto1Id = newResto("Boutique Un", owner1Id);
        resto2Id = newResto("Boutique Deux", owner2Id);
    }

    @AfterEach
    void cleanup() {
        restoRepo.findById(resto1Id).ifPresent(restoRepo::delete);
        restoRepo.findById(resto2Id).ifPresent(restoRepo::delete);
        userRepo.findById(owner1Id).ifPresent(userRepo::delete);
        userRepo.findById(owner2Id).ifPresent(userRepo::delete);
    }

    @Test
    void shop_update_changes_own_shop_only() throws Exception {
        String t2 = token(owner2Email);
        Resp r = postMultipart("/api/v3/seller/shop-update", t2, new String[][]{
                {"_method", "put"}, {"name", "Deux Renommee"}, {"address", "9 avenue"}, {"contact", "+212611110000"},
                {"minimum_order_amount", "0"}, {"free_delivery_status", "false"}, {"free_delivery_over_amount", "0"}
        });
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).get("name").asText()).isEqualTo("Deux Renommee");

        // La boutique du propriétaire 2 reflète le changement.
        Resp info2 = get("/api/v3/seller/shop-info", t2);
        assertThat(M.readTree(info2.body).get("name").asText()).isEqualTo("Deux Renommee");

        // La boutique du propriétaire 1 est INTACTE (id résolu via le contexte, pas depuis le corps).
        assertThat(restoRepo.findById(resto1Id).orElseThrow().getNom()).isEqualTo("Boutique Un");
    }

    private Long newOwner(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("N"); u.setPrenom("P");
        u.setRole(UserRole.RESTAURANT_OWNER);
        u.setIsActive(true);
        return userRepo.save(u).getId();
    }

    private Long newResto(String nom, Long ownerId) {
        Restaurant r = new Restaurant();
        r.setNom(nom);
        r.setDescription("desc");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(userRepo.findById(ownerId).orElseThrow());
        r.setIsActive(true);
        r.setLocalisation(new Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        return restoRepo.save(r).getId();
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
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp postMultipart(String path, String token, String[][] fields) throws Exception {
        String boundary = "----shopupdateboundary";
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
