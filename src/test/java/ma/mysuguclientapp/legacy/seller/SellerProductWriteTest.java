package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.PlatRepository;
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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat écriture produits du shim vendeur : POST /api/v3/seller/products/add (multipart),
 * POST .../update (multipart, {@code _method:put} toléré), POST .../delete {id}. Écrit
 * toujours sous le restaurant du vendeur authentifié (id forcé côté serveur, jamais depuis le
 * corps) ; update/delete d'un Plat d'un AUTRE restaurant -> 404. Voir plan 3c.3.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerProductWriteTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PlatRepository platRepo;
    @Autowired PasswordEncoder encoder;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private final String owner1Email = "prod-w1-" + System.nanoTime() + "@test.mysugu";
    private final String owner2Email = "prod-w2-" + System.nanoTime() + "@test.mysugu";
    private Long owner1Id, owner2Id, resto1Id, resto2Id;
    private Long plat2Id;

    @BeforeEach
    void seed() {
        owner1Id = newOwner(owner1Email);
        owner2Id = newOwner(owner2Email);
        resto1Id = newResto("Boutique Un", owner1Id);
        resto2Id = newResto("Boutique Deux", owner2Id);
        plat2Id = newPlat("Pizza", resto2Id);
    }

    @AfterEach
    void cleanup() {
        platRepo.findById(plat2Id).ifPresent(platRepo::delete);
        restoRepo.findById(resto1Id).ifPresent(restoRepo::delete);
        restoRepo.findById(resto2Id).ifPresent(restoRepo::delete);
        userRepo.findById(owner1Id).ifPresent(userRepo::delete);
        userRepo.findById(owner2Id).ifPresent(userRepo::delete);
    }

    @Test
    void add_creates_product_under_own_restaurant() throws Exception {
        String t1 = token(owner1Email);
        Resp r = postMultipart("/api/v3/seller/products/add", t1, new String[][]{
                {"name", "Tajine Kefta"}, {"details", "Tajine avec kefta"}, {"price", "45.00"}
        });
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).has("message")).isTrue();

        Resp list = get("/api/v3/seller/products/?limit=10&offset=0", t1);
        JsonNode products = M.readTree(list.body).get("products");
        assertThat(products).hasSize(1);
        assertThat(products.get(0).get("name").asText()).isEqualTo("Tajine Kefta");
    }

    @Test
    void update_changes_own_product_name() throws Exception {
        String t1 = token(owner1Email);
        Long id = createPlat(t1, "Avant");

        Resp r = postMultipart("/api/v3/seller/products/update", t1, new String[][]{
                {"_method", "put"}, {"id", String.valueOf(id)}, {"name", "Apres"},
                {"details", "desc"}, {"price", "50.00"}
        });
        assertThat(r.status).isEqualTo(200);

        Resp detail = get("/api/v3/seller/products/details/" + id, t1);
        assertThat(M.readTree(detail.body).get("name").asText()).isEqualTo("Apres");
    }

    @Test
    void update_of_other_restaurant_product_returns_404() throws Exception {
        String t1 = token(owner1Email);
        Resp r = postMultipart("/api/v3/seller/products/update", t1, new String[][]{
                {"id", String.valueOf(plat2Id)}, {"name", "Hack"}, {"price", "1.00"}
        });
        assertThat(r.status).isEqualTo(404);
    }

    @Test
    void delete_removes_own_product() throws Exception {
        String t1 = token(owner1Email);
        Long id = createPlat(t1, "AEffacer");

        Resp authed = postJson("/api/v3/seller/products/delete", t1, Map.of("id", id));
        assertThat(authed.status).isEqualTo(200);

        Resp detail = get("/api/v3/seller/products/details/" + id, t1);
        assertThat(detail.status).isEqualTo(404);
    }

    @Test
    void delete_of_other_restaurant_product_returns_404() throws Exception {
        String t1 = token(owner1Email);
        Resp r = postJson("/api/v3/seller/products/delete", t1, Map.of("id", plat2Id));
        assertThat(r.status).isEqualTo(404);
        // La ressource de l'autre vendeur est INTACTE.
        assertThat(platRepo.findById(plat2Id)).isPresent();
    }

    private Long createPlat(String token, String name) throws Exception {
        Resp r = postMultipart("/api/v3/seller/products/add", token, new String[][]{
                {"name", name}, {"details", "desc"}, {"price", "30.00"}
        });
        Resp list = get("/api/v3/seller/products/?limit=50&offset=0", token);
        JsonNode products = M.readTree(list.body).get("products");
        for (JsonNode p : products) {
            if (p.get("name").asText().equals(name)) {
                return p.get("id").asLong();
            }
        }
        throw new IllegalStateException("Produit créé introuvable dans la liste");
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

    private Long newPlat(String nom, Long restoId) {
        Plat p = new Plat();
        p.setNom(nom);
        p.setDescription("desc " + nom);
        p.setPrix(new BigDecimal("30.00"));
        p.setRestaurant(restoRepo.findById(restoId).orElseThrow());
        p.setIsAvailable(true);
        return platRepo.save(p).getId();
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

    private Resp postJson(String path, String token, Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
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
        String boundary = "----productwriteboundary";
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
