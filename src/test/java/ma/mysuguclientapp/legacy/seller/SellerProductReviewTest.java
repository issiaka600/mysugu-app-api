package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.Avis;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutAvis;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.AvisRepository;
import ma.mysuguclientapp.repositories.CommandeRepository;
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
 * Contrat reviews du shim vendeur : GET products/review-list/{id} et GET shop-product-reviews
 * sont des STUB (Avis est au niveau restaurant, pas produit — umbrella §4 GAP) ; POST
 * shop-product-reviews-status délègue à la modération native des avis
 * (AvisService.moderAvis). Voir plan 3c.7.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerProductReviewTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired CommandeRepository commandeRepo;
    @Autowired AvisRepository avisRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "prod-rev-" + System.nanoTime() + "@test.mysugu";
    private final String clientEmail = "prod-rev-client-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, clientId, restoId, commandeId, avisId;

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

        User client = new User();
        client.setEmail(clientEmail);
        client.setPassword(encoder.encode("demo1234"));
        client.setNom("C"); client.setPrenom("L");
        client.setRole(UserRole.CLIENT);
        client.setIsActive(true);
        client = userRepo.save(client);
        clientId = client.getId();

        Restaurant r = new Restaurant();
        r.setNom("Boutique");
        r.setDescription("desc");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(owner);
        r.setIsActive(true);
        r.setLocalisation(new Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        restoId = restoRepo.save(r).getId();

        Commande c = new Commande();
        c.setNumeroCommande("REV-TEST-" + System.nanoTime());
        c.setClient(client);
        c.setRestaurant(r);
        c.setStatut(StatutCommande.LIVREE);
        c.setStatutPaiement(StatutPaiement.PAYE);
        c.setMontantTotal(new BigDecimal("50.00"));
        commandeId = commandeRepo.save(c).getId();

        Avis avis = Avis.builder()
                .commande(commandeRepo.findById(commandeId).orElseThrow())
                .auteur(client)
                .restaurant(r)
                .noteRestaurant(4)
                .commentaire("Bon plat")
                .statut(StatutAvis.EN_ATTENTE)
                .build();
        avisId = avisRepo.save(avis).getId();
    }

    @AfterEach
    void cleanup() {
        avisRepo.findById(avisId).ifPresent(avisRepo::delete);
        commandeRepo.findById(commandeId).ifPresent(commandeRepo::delete);
        restoRepo.findById(restoId).ifPresent(restoRepo::delete);
        userRepo.findById(clientId).ifPresent(userRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    @Test
    void review_list_returns_empty_benign_envelope() throws Exception {
        String t = token(ownerEmail);
        Resp r = get("/api/v3/seller/products/review-list/999", t);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.get("total_size").asInt()).isEqualTo(0);
        assertThat(n.get("reviews")).isEmpty();
        assertThat(n.get("average_rating").asInt()).isEqualTo(0);
    }

    @Test
    void shop_product_reviews_returns_empty_envelope() throws Exception {
        String t = token(ownerEmail);
        Resp r = get("/api/v3/seller/shop-product-reviews?limit=10&offset=0", t);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.get("total_size").asInt()).isEqualTo(0);
        assertThat(n.get("reviews")).isEmpty();
    }

    @Test
    void shop_product_reviews_status_delegates_to_avis_moderation() throws Exception {
        String t = token(ownerEmail);
        Resp r = postJson("/api/v3/seller/shop-product-reviews-status", t,
                Map.of("id", avisId, "status", 1));
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).has("message")).isTrue();

        assertThat(avisRepo.findById(avisId).orElseThrow().getStatut()).isEqualTo(StatutAvis.APPROUVE);
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
