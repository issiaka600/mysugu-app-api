package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.PaiementRestaurant;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.ModeVersementRestaurant;
import ma.mysuguclientapp.enumerations.StatutPaiementRestaurant;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.PaiementRestaurantRepository;
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
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat GET /api/v3/seller/transactions(?status=&from=&to=) : historique de paiements
 * plateforme -> restaurant ({@link PaiementRestaurant} via
 * {@code FacturationRestaurantServiceImpl.getHistoriquePaiements}), scopé au restaurant du
 * vendeur authentifié via {@link SellerContext#currentRestaurant}. Réponse RÉELLE confirmée
 * contre Tiktak-vendor-app-moso (transaction_controller.dart::getTransactionList fait
 * {@code apiResponse.response!.data.forEach((t) => TransactionModel.fromJson(t))}) : le corps est
 * un TABLEAU JSON NU, PAS l'enveloppe {@code {total_size, limit, offset, transactions:[...]}}
 * supposée par le design initial (déviation 3f.0 vs spec §4). {@code TransactionModel.fromJson}
 * fait aussi {@code int.parse(json['seller_id'].toString())} et
 * {@code double.parse(json['amount'].toString())} SANS garde de nullité -> {@code seller_id} et
 * {@code amount} ne doivent JAMAIS être {@code null} (sinon crash Dart), et
 * {@code created_at} ne doit jamais être null non plus (utilisé sans garde par
 * {@code DateConverter.getMonthIndex(transaction.createdAt!)} lors du filtrage par mois). Jamais
 * 500 ; propriétaire sans restaurant/sans paiement -> tableau vide (jamais 404). Plan 3f.1.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerTransactionsTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PaiementRestaurantRepository paiementRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "tx-" + System.nanoTime() + "@test.mysugu";
    private final String noRestoEmail = "tx-noresto-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, noRestoOwnerId, restoId, paiementId;

    @BeforeEach
    void seed() {
        ownerId = newOwner(ownerEmail);
        noRestoOwnerId = newOwner(noRestoEmail);

        Restaurant r = new Restaurant();
        r.setNom("Boutique Tx");
        r.setDescription("desc");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(userRepo.findById(ownerId).orElseThrow());
        r.setIsActive(true);
        r.setCommissionPourcentage(new BigDecimal("10.00"));
        r.setLocalisation(new ma.mysuguclientapp.entities.Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        restoId = restoRepo.save(r).getId();

        PaiementRestaurant p = PaiementRestaurant.builder()
                .restaurant(restoRepo.findById(restoId).orElseThrow())
                .montantTotal(new BigDecimal("456.75"))
                .modeVersement(ModeVersementRestaurant.VIREMENT_BANCAIRE)
                .reference("PAY-TEST-1")
                .statut(StatutPaiementRestaurant.EFFECTUE)
                .note("virement hebdo")
                .datePaiement(LocalDateTime.now().minusDays(1))
                .build();
        paiementId = paiementRepo.save(p).getId();
    }

    @AfterEach
    void cleanup() {
        if (paiementId != null) paiementRepo.findById(paiementId).ifPresent(paiementRepo::delete);
        if (restoId != null) restoRepo.findById(restoId).ifPresent(restoRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
        userRepo.findById(noRestoOwnerId).ifPresent(userRepo::delete);
    }

    @Test
    void returns_flat_array_with_one_paiement_mapped_to_transaction_row() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/transactions?status=all&from=&to=", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.isArray()).as("body must be a raw JSON array, not an envelope object").isTrue();
        assertThat(n).hasSize(1);

        JsonNode row = n.get(0);
        assertThat(row.get("id").asLong()).isEqualTo(paiementId);
        assertThat(row.get("amount").asDouble()).isEqualTo(456.75);
        assertThat(row.get("seller_id").asLong()).isEqualTo(restoId);
        assertThat(row.has("created_at")).isTrue();
        assertThat(row.get("created_at").isNull()).isFalse();
    }

    @Test
    void owner_with_no_paiements_gets_empty_array_never_errors() throws Exception {
        // vendeur avec restaurant mais sans aucun paiement
        String otherEmail = "tx-nopay-" + System.nanoTime() + "@test.mysugu";
        Long otherOwnerId = newOwner(otherEmail);
        Restaurant r2 = new Restaurant();
        r2.setNom("Boutique Sans Paiement");
        r2.setDescription("desc");
        r2.setTempsLivraisonMoyen(20);
        r2.setOwner(userRepo.findById(otherOwnerId).orElseThrow());
        r2.setIsActive(true);
        r2.setCommissionPourcentage(new BigDecimal("10.00"));
        r2.setLocalisation(new ma.mysuguclientapp.entities.Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        Long otherRestoId = restoRepo.save(r2).getId();
        try {
            String token = token(otherEmail);
            Resp r = get("/api/v3/seller/transactions?status=all&from=&to=", token);
            assertThat(r.status).isEqualTo(200);
            JsonNode n = M.readTree(r.body);
            assertThat(n.isArray()).isTrue();
            assertThat(n).isEmpty();
        } finally {
            restoRepo.findById(otherRestoId).ifPresent(restoRepo::delete);
            userRepo.findById(otherOwnerId).ifPresent(userRepo::delete);
        }
    }

    @Test
    void owner_with_no_restaurant_never_errors_returns_empty_array() throws Exception {
        String token = token(noRestoEmail);
        Resp r = get("/api/v3/seller/transactions?status=all&from=&to=", token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.isArray()).isTrue();
        assertThat(n).isEmpty();
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
