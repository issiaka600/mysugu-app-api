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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat POST /api/v3/seller/orders/delivery-charge-date-update et
 * order-wise-product-upload du shim vendeur : mise à jour des frais/date de livraison
 * (réutilise CommandeService.updateDeliveryChargeAndDate) et upload build-minimal (digital-file
 * irrelevant aux repas, spec §6), tous deux scopés au restaurant du vendeur authentifié. Voir
 * plan 3d.8.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class OrderChargeAndUploadTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PlatRepository platRepo;
    @Autowired CommandeRepository commandeRepo;
    @Autowired PasswordEncoder encoder;

    private final String owner1Email = "ord-chg1-" + System.nanoTime() + "@test.mysugu";
    private final String owner2Email = "ord-chg2-" + System.nanoTime() + "@test.mysugu";
    private final String clientEmail = "ord-chg-client-" + System.nanoTime() + "@test.mysugu";
    private Long owner1Id, owner2Id, clientId, resto1Id, resto2Id, plat1Id, plat2Id;
    private Long commande1Id, commande1bId, commande2Id;

    @BeforeEach
    void seed() {
        owner1Id = newUser(owner1Email, UserRole.RESTAURANT_OWNER).getId();
        owner2Id = newUser(owner2Email, UserRole.RESTAURANT_OWNER).getId();
        User client = newUser(clientEmail, UserRole.CLIENT);
        clientId = client.getId();

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
        userRepo.findById(owner1Id).ifPresent(userRepo::delete);
        userRepo.findById(owner2Id).ifPresent(userRepo::delete);
    }

    @Test
    void charge_date_update_keeps_frozen_amount_and_only_updates_date() throws Exception {
        String t1 = token(owner1Email);
        Resp r = post("/api/v3/seller/orders/delivery-charge-date-update",
                Map.of("_method", "put", "order_id", commande1Id,
                        "deliveryman_charge", "25.50", "expected_delivery_date", "2026-08-01 12:00:00"), t1);
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).has("message")).isTrue();

        Commande updated = commandeRepo.findById(commande1Id).orElseThrow();
        assertThat(updated.getFraisLivraison()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(updated.getMontantFinal()).isEqualByComparingTo(new BigDecimal("110.00"));
        assertThat(updated.getDateLivraisonPrevue()).isNotNull();
    }

    @Test
    void charge_date_update_on_other_restaurant_order_returns_404() throws Exception {
        String t1 = token(owner1Email);
        Resp r = post("/api/v3/seller/orders/delivery-charge-date-update",
                Map.of("_method", "put", "order_id", commande2Id, "deliveryman_charge", "10.00"), t1);
        assertThat(r.status).isEqualTo(404);
    }

    @Test
    void order_wise_product_upload_on_own_order_returns_200_without_crash() throws Exception {
        String t1 = token(owner1Email);
        String boundary = "----boundary-" + UUID.randomUUID();
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"order_id\"\r\n\r\n" + commande1bId + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"_method\"\r\n\r\nput\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"digital_file_after_sell\"; filename=\"f.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\nignored-content\r\n"
                + "--" + boundary + "--\r\n";

        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v3/seller/orders/order-wise-product-upload"))
                .header("Authorization", "Bearer " + t1)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(M.readTree(resp.body()).has("message")).isTrue();
    }

    @Test
    void order_wise_product_upload_on_other_restaurant_order_returns_404() throws Exception {
        String t1 = token(owner1Email);
        String boundary = "----boundary-" + UUID.randomUUID();
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"order_id\"\r\n\r\n" + commande2Id + "\r\n"
                + "--" + boundary + "--\r\n";

        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v3/seller/orders/order-wise-product-upload"))
                .header("Authorization", "Bearer " + t1)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode()).isEqualTo(404);
    }

    private Commande newCommande(User client, Restaurant r, Plat p) {
        Commande c = new Commande();
        c.setNumeroCommande("ORD-CHG-" + System.nanoTime() + "-" + Math.random());
        c.setClient(client);
        c.setRestaurant(r);
        c.setStatut(StatutCommande.EN_ATTENTE);
        c.setStatutPaiement(StatutPaiement.EN_ATTENTE);
        c.setFraisLivraison(new BigDecimal("15.00"));
        c.setMontantTotal(new BigDecimal("115.00"));
        c.setMontantRemise(new BigDecimal("5.00"));
        c.setMontantFinal(new BigDecimal("110.00"));

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
