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
 * Contrat POS produits du shim vendeur : GET /api/v3/seller/pos/products (sans {@code code} —
 * cf. écart avec le modèle Dart réel, spec §3g.0) et GET /api/v3/seller/pos/product-list, tous
 * deux réutilisant PlatService/ProductSellerMapper (comme 3c), scopés au restaurant du vendeur.
 * Jamais 404/500 — cf. plan 3g.1.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerPosProductsTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PlatRepository platRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "pos-prod-" + System.nanoTime() + "@test.mysugu";
    private final String emptyOwnerEmail = "pos-prod-empty-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, emptyOwnerId, restoId, emptyRestoId;
    private Long plat1Id, plat2Id;

    @BeforeEach
    void seed() {
        ownerId = newOwner(ownerEmail);
        emptyOwnerId = newOwner(emptyOwnerEmail);
        restoId = newResto("Boutique POS", ownerId);
        emptyRestoId = newResto("Boutique Vide", emptyOwnerId);

        plat1Id = newPlat("Tajine", restoId);
        plat2Id = newPlat("Couscous", restoId);
    }

    @AfterEach
    void cleanup() {
        platRepo.findById(plat1Id).ifPresent(platRepo::delete);
        platRepo.findById(plat2Id).ifPresent(platRepo::delete);
        restoRepo.findById(restoId).ifPresent(restoRepo::delete);
        restoRepo.findById(emptyRestoId).ifPresent(restoRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
        userRepo.findById(emptyOwnerId).ifPresent(userRepo::delete);
    }

    @Test
    void products_returns_products_envelope_for_owner_restaurant() throws Exception {
        String t = token(ownerEmail);
        Resp r = get("/api/v3/seller/pos/products?limit=10&offset=0", t);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("products")).hasSize(2);
        assertThat(n.get("total_size").asInt()).isEqualTo(2);
        for (JsonNode item : n.get("products")) {
            assertThat(item.has("id")).isTrue();
            assertThat(item.has("name")).isTrue();
            assertThat(item.has("price")).isTrue();
        }
    }

    @Test
    void product_list_search_with_no_match_returns_empty_not_404() throws Exception {
        String t = token(ownerEmail);
        Resp r = get("/api/v3/seller/pos/product-list?name=zzz-no-match&limit=10&offset=0", t);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("products")).isEmpty();
        assertThat(n.get("total_size").asInt()).isEqualTo(0);
    }

    @Test
    void product_list_search_param_alias_still_works() throws Exception {
        String t = token(ownerEmail);
        Resp r = get("/api/v3/seller/pos/product-list?search=Tajine&limit=10&offset=0", t);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("products")).hasSize(1);
        assertThat(n.get("products").get(0).get("name").asText()).isEqualTo("Tajine");
    }

    @Test
    void owner_with_no_plats_returns_empty_envelope_never_500() throws Exception {
        String t = token(emptyOwnerEmail);
        Resp r = get("/api/v3/seller/pos/product-list?limit=10&offset=0", t);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("products")).isEmpty();
        assertThat(n.get("total_size").asInt()).isEqualTo(0);
    }

    @Test
    void products_by_unknown_code_returns_benign_single_product_never_404() throws Exception {
        String t = token(ownerEmail);
        Resp r = get("/api/v3/seller/pos/products?code=999999999", t);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.has("id")).isTrue();
        assertThat(n.has("variation")).isTrue();
        assertThat(n.get("variation")).isEmpty();
    }

    @Test
    void products_by_known_code_returns_owned_product_with_variation_field() throws Exception {
        String t = token(ownerEmail);
        Resp r = get("/api/v3/seller/pos/products?code=" + plat1Id, t);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("name").asText()).isEqualTo("Tajine");
        assertThat(n.has("variation")).isTrue();
        assertThat(n.get("variation")).isEmpty();
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
