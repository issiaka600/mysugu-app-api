package ma.mysuguclientapp.legacy.seller.deliveryman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.GainsLivreur;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.LigneCommande;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.GainsLivreurRepository;
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
 * Task 3e.3: GET delivery-man/details/{id} (roster-guarded), GET delivery-man/order-list/{id}
 * (derived), GET delivery-man/earning/{id} (derive-minimal). NEVER 404/500 — a livreur who never
 * served my restaurant gets a benign neutral shape (roster-guard), not a leak of an arbitrary
 * livreur. Plan 2026-07-10-vendor-3e-deliveryman.md task 3e.3.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerDeliveryManReadTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PlatRepository platRepo;
    @Autowired CommandeRepository commandeRepo;
    @Autowired GainsLivreurRepository gainsRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "dm-read-owner-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, clientId, livreurInRosterId, livreurOutsideId, restoId, restoOtherId, platId, platOtherId;
    private Long commandeId, gainsId;

    @BeforeEach
    void seed() {
        ownerId = newUser("dm-read-owner-", UserRole.RESTAURANT_OWNER, ownerEmail).getId();
        User ownerOther = newUser("dm-read-owner2-", UserRole.RESTAURANT_OWNER, null);
        User client = newUser("dm-read-client-", UserRole.CLIENT, null);
        clientId = client.getId();
        livreurInRosterId = newUser("dm-read-livreurIn-", UserRole.LIVREUR, null).getId();
        livreurOutsideId = newUser("dm-read-livreurOut-", UserRole.LIVREUR, null).getId();

        Restaurant resto = newResto("Boutique Read", userRepo.findById(ownerId).orElseThrow());
        restoId = resto.getId();
        Restaurant restoOther = newResto("Boutique Autre Read", ownerOther);
        restoOtherId = restoOther.getId();

        Plat plat = newPlat("Tajine", resto);
        platId = plat.getId();
        Plat platOther = newPlat("Pizza", restoOther);
        platOtherId = platOther.getId();

        User livreurIn = userRepo.findById(livreurInRosterId).orElseThrow();
        User livreurOut = userRepo.findById(livreurOutsideId).orElseThrow();

        Commande c = newCommande(client, resto, plat, livreurIn);
        commandeId = c.getId();
        // livreurOutsideId only ever served restoOther, never resto (mine) -> NOT in my roster.
        newCommande(client, restoOther, platOther, livreurOut);

        GainsLivreur gains = new GainsLivreur();
        gains.setLivreur(livreurIn);
        gains.setCommande(c);
        gains.setMontant(new BigDecimal("20.00"));
        gains.setFraisLivraison(new BigDecimal("20.00"));
        gains.setCommissionPlateforme(BigDecimal.ZERO);
        gains.setMontantNet(new BigDecimal("18.00"));
        gains.setEstPaye(false);
        gainsId = gainsRepo.save(gains).getId();
    }

    @AfterEach
    void cleanup() {
        gainsRepo.findById(gainsId).ifPresent(gainsRepo::delete);
        commandeRepo.findAllById(commandeRepo.findByRestaurantIdOrderByCreatedAtDesc(restoId).stream().map(Commande::getId).toList())
                .forEach(commandeRepo::delete);
        commandeRepo.findAllById(commandeRepo.findByRestaurantIdOrderByCreatedAtDesc(restoOtherId).stream().map(Commande::getId).toList())
                .forEach(commandeRepo::delete);
        platRepo.findById(platId).ifPresent(platRepo::delete);
        platRepo.findById(platOtherId).ifPresent(platRepo::delete);
        Long ownerOtherId = restoRepo.findById(restoOtherId).map(r -> r.getOwner().getId()).orElse(null);
        restoRepo.findById(restoId).ifPresent(restoRepo::delete);
        restoRepo.findById(restoOtherId).ifPresent(restoRepo::delete);
        userRepo.findById(clientId).ifPresent(userRepo::delete);
        userRepo.findById(livreurInRosterId).ifPresent(userRepo::delete);
        userRepo.findById(livreurOutsideId).ifPresent(userRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
        if (ownerOtherId != null) {
            userRepo.findById(ownerOtherId).ifPresent(userRepo::delete);
        }
    }

    @Test
    void details_for_livreur_in_roster_returns_delivery_man_object() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/delivery-man/details/" + livreurInRosterId, token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.has("delivery_man")).isTrue();
        assertThat(n.get("delivery_man").isNull()).isFalse();
        assertThat(n.get("delivery_man").get("id").asLong()).isEqualTo(livreurInRosterId);
        assertThat(n.has("withdrawbale_balance")).isTrue();
    }

    @Test
    void details_for_livreur_outside_roster_is_benign_never_404() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/delivery-man/details/" + livreurOutsideId, token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.has("delivery_man")).isTrue();
        assertThat(n.get("delivery_man").isNull()).isTrue();
    }

    @Test
    void details_for_unknown_id_is_benign_never_404() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/delivery-man/details/999999999", token);
        assertThat(r.status).isEqualTo(200);
    }

    @Test
    void order_list_for_livreur_in_roster_returns_my_commandes() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/delivery-man/order-list/" + livreurInRosterId, token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.has("orders")).isTrue();
        assertThat(n.get("orders").isArray()).isTrue();
        assertThat(n.get("orders")).hasSize(1);
        assertThat(n.get("orders").get(0).get("id").asLong()).isEqualTo(commandeId);
    }

    @Test
    void order_list_for_livreur_outside_roster_is_empty_never_404() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/delivery-man/order-list/" + livreurOutsideId, token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("orders")).isEmpty();
    }

    @Test
    void earning_for_livreur_in_roster_sums_montant_net_scoped_to_my_restaurant() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/delivery-man/earning/" + livreurInRosterId, token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.has("total_earn")).isTrue();
        assertThat(n.get("total_earn").isNull()).isFalse();
        assertThat(n.get("total_earn").asDouble()).isEqualTo(18.00);
        assertThat(n.has("withdrawable_balance")).isTrue();
        assertThat(n.get("withdrawable_balance").isNull()).isFalse();
    }

    @Test
    void earning_for_livreur_outside_roster_is_zero_never_404() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/delivery-man/earning/" + livreurOutsideId, token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("total_earn").asDouble()).isEqualTo(0.0);
    }

    private Commande newCommande(User client, Restaurant r, Plat p, User livreur) {
        Commande c = new Commande();
        c.setNumeroCommande("ORD-DM-READ-" + System.nanoTime() + "-" + Math.random());
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
