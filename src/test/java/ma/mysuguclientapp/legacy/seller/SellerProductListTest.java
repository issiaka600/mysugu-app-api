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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat produits (liste + détails/édition) du shim vendeur : GET /api/v3/seller/products/,
 * GET /api/v3/seller/products/details/{id}, GET /api/v3/seller/products/edit/{id}.
 * Tout est scopé au restaurant du vendeur authentifié via SellerContext ; un Plat d'un AUTRE
 * restaurant -> 404 (jamais de fuite cross-restaurant), voir plan 3c.2 + garde de sécurité
 * critique de la tranche (isolation cross-restaurant).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerProductListTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PlatRepository platRepo;
    @Autowired PasswordEncoder encoder;

    private final String owner1Email = "prod-list1-" + System.nanoTime() + "@test.mysugu";
    private final String owner2Email = "prod-list2-" + System.nanoTime() + "@test.mysugu";
    private Long owner1Id, owner2Id, resto1Id, resto2Id;
    private Long plat1aId, plat1bId, plat2aId;

    @BeforeEach
    void seed() {
        owner1Id = newOwner(owner1Email);
        owner2Id = newOwner(owner2Email);
        resto1Id = newResto("Boutique Un", owner1Id);
        resto2Id = newResto("Boutique Deux", owner2Id);

        plat1aId = newPlat("Tajine", resto1Id);
        plat1bId = newPlat("Couscous", resto1Id);
        plat2aId = newPlat("Pizza", resto2Id);
    }

    @AfterEach
    void cleanup() {
        platRepo.findById(plat1aId).ifPresent(platRepo::delete);
        platRepo.findById(plat1bId).ifPresent(platRepo::delete);
        platRepo.findById(plat2aId).ifPresent(platRepo::delete);
        restoRepo.findById(resto1Id).ifPresent(restoRepo::delete);
        restoRepo.findById(resto2Id).ifPresent(restoRepo::delete);
        userRepo.findById(owner1Id).ifPresent(userRepo::delete);
        userRepo.findById(owner2Id).ifPresent(userRepo::delete);
    }

    @Test
    void list_returns_only_own_restaurant_products() throws Exception {
        String t1 = token(owner1Email);
        Resp r = get("/api/v3/seller/products/?limit=10&offset=0", t1);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("products")).hasSize(2);
        assertThat(n.has("total_size")).isTrue();
    }

    @Test
    void details_returns_own_product() throws Exception {
        String t1 = token(owner1Email);
        Resp r = get("/api/v3/seller/products/details/" + plat1aId, t1);
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).get("name").asText()).isEqualTo("Tajine");
    }

    @Test
    void edit_returns_own_product() throws Exception {
        String t1 = token(owner1Email);
        Resp r = get("/api/v3/seller/products/edit/" + plat1aId, t1);
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).get("name").asText()).isEqualTo("Tajine");
    }

    @Test
    void details_of_other_restaurant_product_returns_404() throws Exception {
        String t1 = token(owner1Email);
        Resp r = get("/api/v3/seller/products/details/" + plat2aId, t1);
        assertThat(r.status).isEqualTo(404);
    }

    @Test
    void edit_of_other_restaurant_product_returns_404() throws Exception {
        String t1 = token(owner1Email);
        Resp r = get("/api/v3/seller/products/edit/" + plat2aId, t1);
        assertThat(r.status).isEqualTo(404);
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

    private Resp get(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
