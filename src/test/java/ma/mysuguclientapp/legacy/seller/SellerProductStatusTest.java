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
 * Contrat status-update du shim vendeur : POST /api/v3/seller/products/status-update {id,status}
 * bascule Plat.isAvailable via PlatService.updateAvailability. Un Plat d'un AUTRE restaurant
 * -> 404. Voir plan 3c.4.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerProductStatusTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PlatRepository platRepo;
    @Autowired PasswordEncoder encoder;

    private final String owner1Email = "prod-st1-" + System.nanoTime() + "@test.mysugu";
    private final String owner2Email = "prod-st2-" + System.nanoTime() + "@test.mysugu";
    private Long owner1Id, owner2Id, resto1Id, resto2Id, plat1Id, plat2Id;

    @BeforeEach
    void seed() {
        owner1Id = newOwner(owner1Email);
        owner2Id = newOwner(owner2Email);
        resto1Id = newResto("Boutique Un", owner1Id);
        resto2Id = newResto("Boutique Deux", owner2Id);
        plat1Id = newPlat("Tajine", resto1Id);
        plat2Id = newPlat("Pizza", resto2Id);
    }

    @AfterEach
    void cleanup() {
        platRepo.findById(plat1Id).ifPresent(platRepo::delete);
        platRepo.findById(plat2Id).ifPresent(platRepo::delete);
        restoRepo.findById(resto1Id).ifPresent(restoRepo::delete);
        restoRepo.findById(resto2Id).ifPresent(restoRepo::delete);
        userRepo.findById(owner1Id).ifPresent(userRepo::delete);
        userRepo.findById(owner2Id).ifPresent(userRepo::delete);
    }

    @Test
    void status_update_flips_availability_off_then_on() throws Exception {
        String t1 = token(owner1Email);

        Resp off = postJson("/api/v3/seller/products/status-update", t1, Map.of("id", plat1Id, "status", 0));
        assertThat(off.status).isEqualTo(200);

        Resp detail = get("/api/v3/seller/products/details/" + plat1Id, t1);
        JsonNode n = M.readTree(detail.body);
        assertThat(n.get("status").asInt()).isEqualTo(0);

        Resp on = postJson("/api/v3/seller/products/status-update", t1, Map.of("id", plat1Id, "status", 1));
        assertThat(on.status).isEqualTo(200);
        Resp detail2 = get("/api/v3/seller/products/details/" + plat1Id, t1);
        assertThat(M.readTree(detail2.body).get("status").asInt()).isEqualTo(1);
    }

    @Test
    void status_update_of_other_restaurant_product_returns_404() throws Exception {
        String t1 = token(owner1Email);
        Resp r = postJson("/api/v3/seller/products/status-update", t1, Map.of("id", plat2Id, "status", 0));
        assertThat(r.status).isEqualTo(404);
        assertThat(platRepo.findById(plat2Id).orElseThrow().getIsAvailable()).isTrue();
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

    private record Resp(int status, String body) {}
}
