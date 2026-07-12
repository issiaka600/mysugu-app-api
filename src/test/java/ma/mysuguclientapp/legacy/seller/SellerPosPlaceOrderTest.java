package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.CommandeRepository;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat POS place-order du shim vendeur : POST /api/v3/seller/pos/place-order.
 * BUILD-MINIMAL — le panier POS est mappé sur CommandeCreateDTO et créé via
 * CommandeService.createCommande, le vendeur authentifié servant de client "walk-in" (User RÉEL
 * déjà persisté, jamais un client synthétique). Panier vide/invalide -> succès bénin, jamais 500.
 * Cf. plan 3g.2.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerPosPlaceOrderTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PlatRepository platRepo;
    @Autowired CommandeRepository commandeRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "pos-order-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, restoId, plat1Id, plat2Id;

    @BeforeEach
    void seed() {
        ownerId = newOwner(ownerEmail);
        restoId = newResto("Boutique POS Order", ownerId);
        plat1Id = newPlat("Tajine", restoId);
        plat2Id = newPlat("Couscous", restoId);
    }

    @AfterEach
    void cleanup() {
        // Purge des commandes (FK: lignes) avant les plats/restaurant/users.
        commandeRepo.findByRestaurantIdOrderByCreatedAtDesc(restoId)
                .forEach(c -> commandeRepo.deleteById(c.getId()));
        platRepo.findById(plat1Id).ifPresent(platRepo::delete);
        platRepo.findById(plat2Id).ifPresent(platRepo::delete);
        restoRepo.findById(restoId).ifPresent(restoRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    @Test
    void place_order_creates_commande_and_returns_success() throws Exception {
        int before = commandeRepo.findByRestaurantIdOrderByCreatedAtDesc(restoId).size();
        String t = token(ownerEmail);

        Map<String, Object> cart = Map.of(
                "cart", List.of(
                        Map.of("id", plat1Id, "quantity", 2, "price", "30.00"),
                        Map.of("id", plat2Id, "quantity", 1, "price", "30.00")),
                "payment_method", "cash",
                "customer_id", 0);
        Resp r = post("/api/v3/seller/pos/place-order", cart, t);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("message").asText()).isNotBlank();

        int after = commandeRepo.findByRestaurantIdOrderByCreatedAtDesc(restoId).size();
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void empty_cart_returns_benign_success_not_500() throws Exception {
        int before = commandeRepo.findByRestaurantIdOrderByCreatedAtDesc(restoId).size();
        String t = token(ownerEmail);

        Resp r = post("/api/v3/seller/pos/place-order", Map.of("cart", List.of()), t);
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).get("message").asText()).isNotBlank();

        int after = commandeRepo.findByRestaurantIdOrderByCreatedAtDesc(restoId).size();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void malformed_cart_never_500() throws Exception {
        String t = token(ownerEmail);
        Resp r = post("/api/v3/seller/pos/place-order",
                Map.of("cart", List.of(Map.of("id", "not-a-number", "quantity", "x"))), t);
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).get("message").asText()).isNotBlank();
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
        Resp r = post("/api/v3/seller/auth/login", Map.of("email", email, "password", "demo1234"), null);
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

    private record Resp(int status, String body) {}
}
