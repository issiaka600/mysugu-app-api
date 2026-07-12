package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.LigneCommande;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat top-selling/most-popular du shim vendeur : agrégation LigneCommande (build-minimal),
 * scopée au restaurant du vendeur. Un restaurant sans commande -> enveloppe vide (jamais 500).
 * Voir plan 3c.8.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerTopSellingTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PlatRepository platRepo;
    @Autowired CommandeRepository commandeRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "prod-top-" + System.nanoTime() + "@test.mysugu";
    private final String clientEmail = "prod-top-client-" + System.nanoTime() + "@test.mysugu";
    private final String emptyOwnerEmail = "prod-top-empty-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, clientId, emptyOwnerId, restoId, emptyRestoId, platAId, platBId, commandeId;

    @BeforeEach
    void seed() {
        User owner = newUser(ownerEmail, UserRole.RESTAURANT_OWNER);
        ownerId = owner.getId();
        User emptyOwner = newUser(emptyOwnerEmail, UserRole.RESTAURANT_OWNER);
        emptyOwnerId = emptyOwner.getId();
        User client = newUser(clientEmail, UserRole.CLIENT);
        clientId = client.getId();

        Restaurant r = newResto("Boutique Top", owner);
        restoId = r.getId();
        Restaurant emptyResto = newResto("Boutique Vide", emptyOwner);
        emptyRestoId = emptyResto.getId();

        Plat platA = newPlat("Populaire", r);
        platAId = platA.getId();
        Plat platB = newPlat("Discret", r);
        platBId = platB.getId();

        Commande c = new Commande();
        c.setNumeroCommande("TOP-TEST-" + System.nanoTime());
        c.setClient(client);
        c.setRestaurant(r);
        c.setStatut(StatutCommande.LIVREE);
        c.setStatutPaiement(StatutPaiement.PAYE);
        c.setMontantTotal(new BigDecimal("100.00"));

        List<LigneCommande> lignes = new ArrayList<>();
        lignes.add(ligne(c, platA, 5));
        lignes.add(ligne(c, platB, 1));
        c.setLignesCommande(lignes);

        commandeId = commandeRepo.save(c).getId();
    }

    private LigneCommande ligne(Commande c, Plat p, int qty) {
        LigneCommande l = new LigneCommande();
        l.setCommande(c);
        l.setPlat(p);
        l.setQuantite(qty);
        l.setPrixUnitaire(p.getPrix());
        l.setMontantTotal(p.getPrix().multiply(BigDecimal.valueOf(qty)));
        return l;
    }

    @AfterEach
    void cleanup() {
        commandeRepo.findById(commandeId).ifPresent(commandeRepo::delete);
        platRepo.findById(platAId).ifPresent(platRepo::delete);
        platRepo.findById(platBId).ifPresent(platRepo::delete);
        restoRepo.findById(restoId).ifPresent(restoRepo::delete);
        restoRepo.findById(emptyRestoId).ifPresent(restoRepo::delete);
        userRepo.findById(clientId).ifPresent(userRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
        userRepo.findById(emptyOwnerId).ifPresent(userRepo::delete);
    }

    @Test
    void top_selling_orders_by_quantity_desc_with_count() throws Exception {
        String t = token(ownerEmail);
        Resp r = get("/api/v3/seller/products/top-selling-product?limit=10&offset=0", t);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("products")).hasSize(2);
        assertThat(n.get("products").get(0).get("name").asText()).isEqualTo("Populaire");
        assertThat(n.get("products").get(0).get("count").asInt()).isEqualTo(5);
    }

    @Test
    void most_popular_orders_by_quantity_desc_with_count() throws Exception {
        String t = token(ownerEmail);
        Resp r = get("/api/v3/seller/products/most-popular-product?limit=10&offset=0", t);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("products")).hasSize(2);
        assertThat(n.get("products").get(0).get("name").asText()).isEqualTo("Populaire");
    }

    @Test
    void top_selling_of_restaurant_without_orders_returns_empty_envelope() throws Exception {
        String t = token(emptyOwnerEmail);
        Resp r = get("/api/v3/seller/products/top-selling-product?limit=10&offset=0", t);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.get("total_size").asInt()).isEqualTo(0);
        assertThat(n.get("products")).isEmpty();
    }

    private User newUser(String email, UserRole role) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("N"); u.setPrenom("P");
        u.setRole(role);
        u.setIsActive(true);
        return userRepo.save(u);
    }

    private Restaurant newResto(String nom, User owner) {
        Restaurant r = new Restaurant();
        r.setNom(nom);
        r.setDescription("desc");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(owner);
        r.setIsActive(true);
        r.setLocalisation(new Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        return restoRepo.save(r);
    }

    private Plat newPlat(String nom, Restaurant r) {
        Plat p = new Plat();
        p.setNom(nom);
        p.setDescription("desc " + nom);
        p.setPrix(new BigDecimal("30.00"));
        p.setRestaurant(r);
        p.setIsAvailable(true);
        return platRepo.save(p);
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
