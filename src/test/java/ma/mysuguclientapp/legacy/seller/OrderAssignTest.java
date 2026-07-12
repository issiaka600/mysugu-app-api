package ma.mysuguclientapp.legacy.seller;

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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat POST /api/v3/seller/orders/assign-delivery-man et assign-third-party-delivery du shim
 * vendeur : assignation d'un livreur (interne ou tiers), scopée au restaurant du vendeur
 * authentifié. Une commande d'un AUTRE restaurant -> 404 (jamais de mutation cross-restaurant) —
 * garde de sécurité critique. Voir plan 3d.6.
 *
 * // GAP: vendor->livreur ownership n'est pas natif (livreurs sont globaux, gérés par l'admin) —
 * l'assignation réutilise directement CommandeService.assignLivreur (umbrella §4).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class OrderAssignTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PlatRepository platRepo;
    @Autowired CommandeRepository commandeRepo;
    @Autowired PasswordEncoder encoder;

    private final String owner1Email = "ord-asgn1-" + System.nanoTime() + "@test.mysugu";
    private final String owner2Email = "ord-asgn2-" + System.nanoTime() + "@test.mysugu";
    private final String clientEmail = "ord-asgn-client-" + System.nanoTime() + "@test.mysugu";
    private final String livreurEmail = "ord-asgn-livreur-" + System.nanoTime() + "@test.mysugu";
    private Long owner1Id, owner2Id, clientId, livreurId, resto1Id, resto2Id, plat1Id, plat2Id;
    private Long commande1Id, commande1bId, commande2Id;

    @BeforeEach
    void seed() {
        owner1Id = newUser(owner1Email, UserRole.RESTAURANT_OWNER).getId();
        owner2Id = newUser(owner2Email, UserRole.RESTAURANT_OWNER).getId();
        User client = newUser(clientEmail, UserRole.CLIENT);
        clientId = client.getId();
        User livreur = newUser(livreurEmail, UserRole.LIVREUR);
        livreur.setLivreurDisponible(true);
        livreurId = userRepo.save(livreur).getId();

        Restaurant r1 = newResto("Boutique Un", userRepo.findById(owner1Id).orElseThrow());
        resto1Id = r1.getId();
        Restaurant r2 = newResto("Boutique Deux", userRepo.findById(owner2Id).orElseThrow());
        resto2Id = r2.getId();

        Plat p1 = newPlat("Tajine", r1);
        plat1Id = p1.getId();
        Plat p2 = newPlat("Pizza", r2);
        plat2Id = p2.getId();

        commande1Id = newCommande(client, r1, p1).getId();
        commande1bId = newCommande(client, r1, p1).getId();
        commande2Id = newCommande(client, r2, p2).getId();
    }

    @AfterEach
    void cleanup() {
        commandeRepo.findById(commande1Id).ifPresent(commandeRepo::delete);
        commandeRepo.findById(commande1bId).ifPresent(commandeRepo::delete);
        commandeRepo.findById(commande2Id).ifPresent(commandeRepo::delete);
        platRepo.findById(plat1Id).ifPresent(platRepo::delete);
        platRepo.findById(plat2Id).ifPresent(platRepo::delete);
        restoRepo.findById(resto1Id).ifPresent(restoRepo::delete);
        restoRepo.findById(resto2Id).ifPresent(restoRepo::delete);
        userRepo.findById(clientId).ifPresent(userRepo::delete);
        userRepo.findById(livreurId).ifPresent(userRepo::delete);
        userRepo.findById(owner1Id).ifPresent(userRepo::delete);
        userRepo.findById(owner2Id).ifPresent(userRepo::delete);
    }

    @Test
    void assign_delivery_man_on_own_order_sets_livreur() throws Exception {
        String t1 = token(owner1Email);
        Resp r = post("/api/v3/seller/orders/assign-delivery-man",
                Map.of("_method", "put", "order_id", commande1Id, "delivery_man_id", livreurId), t1);
        assertThat(r.status).isEqualTo(200);

        Commande updated = commandeRepo.findById(commande1Id).orElseThrow();
        assertThat(updated.getLivreur()).isNotNull();
        assertThat(updated.getLivreur().getId()).isEqualTo(livreurId);
    }

    @Test
    void assign_delivery_man_on_other_restaurant_order_returns_404() throws Exception {
        String t1 = token(owner1Email);
        Resp r = post("/api/v3/seller/orders/assign-delivery-man",
                Map.of("_method", "put", "order_id", commande2Id, "delivery_man_id", livreurId), t1);
        assertThat(r.status).isEqualTo(404);
    }

    @Test
    void assign_third_party_delivery_on_own_order_sets_tiers_fields() throws Exception {
        String t1 = token(owner1Email);
        Resp r = post("/api/v3/seller/orders/assign-third-party-delivery",
                Map.of("delivery_service_name", "Glovo", "third_party_delivery_tracking_id", "TRK-1",
                        "order_id", commande1bId), t1);
        assertThat(r.status).isEqualTo(200);

        Commande updated = commandeRepo.findById(commande1bId).orElseThrow();
        assertThat(updated.getLivreurTiersNom()).isEqualTo("Glovo");
    }

    @Test
    void assign_third_party_delivery_on_other_restaurant_order_returns_404() throws Exception {
        String t1 = token(owner1Email);
        Resp r = post("/api/v3/seller/orders/assign-third-party-delivery",
                Map.of("delivery_service_name", "Glovo", "third_party_delivery_tracking_id", "TRK-2",
                        "order_id", commande2Id), t1);
        assertThat(r.status).isEqualTo(404);
    }

    private Commande newCommande(User client, Restaurant r, Plat p) {
        Commande c = new Commande();
        c.setNumeroCommande("ORD-ASGN-" + System.nanoTime() + "-" + Math.random());
        c.setClient(client);
        c.setRestaurant(r);
        c.setStatut(StatutCommande.EN_ATTENTE);
        c.setStatutPaiement(StatutPaiement.EN_ATTENTE);
        c.setMontantTotal(new BigDecimal("100.00"));
        c.setMontantFinal(new BigDecimal("100.00"));

        LigneCommande ligne = new LigneCommande();
        ligne.setCommande(c);
        ligne.setPlat(p);
        ligne.setQuantite(1);
        ligne.setPrixUnitaire(p.getPrix());
        ligne.setMontantTotal(p.getPrix());
        c.setLignesCommande(List.of(ligne));

        return commandeRepo.save(c);
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
