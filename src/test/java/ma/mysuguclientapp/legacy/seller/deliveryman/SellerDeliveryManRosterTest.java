package ma.mysuguclientapp.legacy.seller.deliveryman;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3e.2: GET /api/v3/seller/seller-delivery-man (bare array, 3e.0 deviation) and
 * GET /api/v3/seller/delivery-man/list (envelope, key delivery_man) — DERIVED roster (distinct
 * livreurs who served MY restaurant). Never 404/500, empty roster -> empty benign shape.
 * Plan 2026-07-10-vendor-3e-deliveryman.md task 3e.2.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerDeliveryManRosterTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PlatRepository platRepo;
    @Autowired CommandeRepository commandeRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerServedEmail = "dm-roster-owner1-" + System.nanoTime() + "@test.mysugu";
    private final String ownerEmptyEmail = "dm-roster-owner2-" + System.nanoTime() + "@test.mysugu";
    private Long ownerServedId, ownerEmptyId, clientId, livreurId, restoServedId, restoEmptyId, platId;
    private Long commandeId;

    @BeforeEach
    void seed() {
        ownerServedId = newUser("dm-roster-owner1-", UserRole.RESTAURANT_OWNER, ownerServedEmail).getId();
        ownerEmptyId = newUser("dm-roster-owner2-", UserRole.RESTAURANT_OWNER, ownerEmptyEmail).getId();
        User client = newUser("dm-roster-client-", UserRole.CLIENT, null);
        clientId = client.getId();
        User livreur = newUser("dm-roster-livreur-", UserRole.LIVREUR, null);
        livreurId = livreur.getId();

        Restaurant restoServed = newResto("Boutique Servie", userRepo.findById(ownerServedId).orElseThrow());
        restoServedId = restoServed.getId();
        Restaurant restoEmpty = newResto("Boutique Vide", userRepo.findById(ownerEmptyId).orElseThrow());
        restoEmptyId = restoEmpty.getId();

        Plat plat = newPlat("Tajine", restoServed);
        platId = plat.getId();

        commandeId = newCommande(client, restoServed, plat, livreur).getId();
    }

    @AfterEach
    void cleanup() {
        commandeRepo.findById(commandeId).ifPresent(commandeRepo::delete);
        platRepo.findById(platId).ifPresent(platRepo::delete);
        restoRepo.findById(restoServedId).ifPresent(restoRepo::delete);
        restoRepo.findById(restoEmptyId).ifPresent(restoRepo::delete);
        userRepo.findById(clientId).ifPresent(userRepo::delete);
        userRepo.findById(livreurId).ifPresent(userRepo::delete);
        userRepo.findById(ownerServedId).ifPresent(userRepo::delete);
        userRepo.findById(ownerEmptyId).ifPresent(userRepo::delete);
    }

    @Test
    void seller_delivery_man_returns_bare_array_with_served_livreur() throws Exception {
        String token = token(ownerServedEmail);
        Resp r = get("/api/v3/seller/seller-delivery-man", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.isArray()).isTrue();
        assertThat(n).hasSize(1);
        assertThat(n.get(0).get("id").asLong()).isEqualTo(livreurId);
        assertThat(n.get(0).has("seller_id")).isTrue();
        assertThat(n.get(0).get("seller_id").isNull()).isFalse();
    }

    @Test
    void seller_delivery_man_returns_empty_array_never_404_when_no_livreur_served_me() throws Exception {
        String token = token(ownerEmptyEmail);
        Resp r = get("/api/v3/seller/seller-delivery-man", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.isArray()).isTrue();
        assertThat(n).isEmpty();
    }

    @Test
    void delivery_man_list_mirrors_roster_with_delivery_man_key() throws Exception {
        String token = token(ownerServedEmail);
        Resp r = get("/api/v3/seller/delivery-man/list", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.has("total_size")).isTrue();
        assertThat(n.has("limit")).isTrue();
        assertThat(n.has("offset")).isTrue();
        assertThat(n.has("delivery_man")).isTrue();
        assertThat(n.get("delivery_man").isArray()).isTrue();
        assertThat(n.get("delivery_man")).hasSize(1);
        assertThat(n.get("total_size").asInt()).isEqualTo(1);
        // FRAGILE per 3e.0: is_online must be a non-null int, never omitted.
        assertThat(n.get("delivery_man").get(0).has("is_online")).isTrue();
        assertThat(n.get("delivery_man").get(0).get("is_online").isNull()).isFalse();
    }

    @Test
    void delivery_man_list_returns_empty_never_404_when_no_livreur_served_me() throws Exception {
        String token = token(ownerEmptyEmail);
        Resp r = get("/api/v3/seller/delivery-man/list", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("delivery_man").isArray()).isTrue();
        assertThat(n.get("delivery_man")).isEmpty();
        assertThat(n.get("total_size").asInt()).isEqualTo(0);
    }

    private Commande newCommande(User client, Restaurant r, Plat p, User livreur) {
        Commande c = new Commande();
        c.setNumeroCommande("ORD-DM-ROSTER-" + System.nanoTime() + "-" + Math.random());
        c.setClient(client);
        c.setRestaurant(r);
        c.setLivreur(livreur);
        c.setStatut(StatutCommande.LIVREE);
        c.setStatutPaiement(StatutPaiement.PAYE);
        c.setMontantTotal(new BigDecimal("100.00"));
        c.setMontantFinal(new BigDecimal("100.00"));

        LigneCommande ligne = new LigneCommande();
        ligne.setCommande(c);
        ligne.setPlat(p);
        ligne.setQuantite(1);
        ligne.setPrixUnitaire(p.getPrix());
        ligne.setMontantTotal(p.getPrix());
        c.setLignesCommande(java.util.List.of(ligne));

        return commandeRepo.save(c);
    }

    private User newUser(String prefix, UserRole role, String forcedEmail) {
        User u = new User();
        u.setEmail(forcedEmail != null ? forcedEmail : prefix + System.nanoTime() + "@test.mysugu");
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
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
