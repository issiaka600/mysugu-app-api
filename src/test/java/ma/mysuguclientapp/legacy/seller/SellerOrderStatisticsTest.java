package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.seller.mapper.SellerStatsMapper;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat GET /api/v3/seller/order-statistics?statistics_type= du shim vendeur : mappe
 * {@link RestaurantDashboardDTO} (buckets natifs) -> objet compteurs 6valley
 * (pending/confirmed/processing/out_for_delivery/delivered/canceled/returned/failed/total),
 * clés confirmées via Tiktak-vendor-app-moso (BusinessAnalyticsFilterDataModel, 3j.0). Deux
 * volets : sélection de bucket pure (mapper, valeurs distinctes) + câblage bout-en-bout
 * (ownership/no-500). Plan 3j.1.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerOrderStatisticsTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final SellerStatsMapper mapper = new SellerStatsMapper();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "stats-order-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, restoId;

    @BeforeEach
    void seed() {
        User owner = new User();
        owner.setEmail(ownerEmail);
        owner.setPassword(encoder.encode("demo1234"));
        owner.setNom("N"); owner.setPrenom("P");
        owner.setRole(UserRole.RESTAURANT_OWNER);
        owner.setIsActive(true);
        ownerId = userRepo.save(owner).getId();

        Restaurant r = new Restaurant();
        r.setNom("Boutique Stats");
        r.setDescription("desc");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(userRepo.findById(ownerId).orElseThrow());
        r.setIsActive(true);
        r.setLocalisation(new ma.mysuguclientapp.entities.Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        restoId = restoRepo.save(r).getId();
    }

    @AfterEach
    void cleanup() {
        restoRepo.findById(restoId).ifPresent(restoRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    private static Map<StatutCommande, Long> counts(Object... pairs) {
        Map<StatutCommande, Long> m = new EnumMap<>(StatutCommande.class);
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((StatutCommande) pairs[i], (Long) pairs[i + 1]);
        }
        return m;
    }

    @Test
    void mapper_maps_each_status_to_its_6valley_bucket() {
        Map<String, Object> m = mapper.orderStatistics(counts(
                StatutCommande.EN_ATTENTE, 2L,
                StatutCommande.CONFIRMEE, 1L,
                StatutCommande.EN_PREPARATION, 3L,
                StatutCommande.PRETE, 1L,
                StatutCommande.ASSIGNEE_LIVREUR, 1L,
                StatutCommande.EN_COURS, 2L,
                StatutCommande.LIVREE, 5L,
                StatutCommande.ANNULEE, 1L,
                StatutCommande.NON_FINALISEE, 1L));
        assertThat(m.get("pending")).isEqualTo(2L);
        assertThat(m.get("confirmed")).isEqualTo(1L);
        assertThat(m.get("processing")).isEqualTo(3L);
        assertThat(m.get("ready")).isEqualTo(1L);
        assertThat(m.get("out_for_delivery")).isEqualTo(3L);  // ASSIGNEE_LIVREUR + EN_COURS
        assertThat(m.get("delivered")).isEqualTo(5L);
        assertThat(m.get("canceled")).isEqualTo(1L);
        assertThat(m.get("returned")).isEqualTo(0L);
        assertThat(m.get("failed")).isEqualTo(1L);
        assertThat(m.get("total")).isEqualTo(17L);            // somme de tous les statuts
    }

    @Test
    void mapper_confirmed_and_out_for_delivery_are_not_duplicates() {
        // Régression : anciennement confirmed=processing et out_for_delivery=pending (compteurs faux).
        Map<String, Object> m = mapper.orderStatistics(counts(
                StatutCommande.EN_ATTENTE, 7L,      // pending
                StatutCommande.CONFIRMEE, 2L,       // confirmed
                StatutCommande.EN_PREPARATION, 3L,  // processing
                StatutCommande.EN_COURS, 1L));      // out_for_delivery
        assertThat(m.get("pending")).isEqualTo(7L);
        assertThat(m.get("confirmed")).isEqualTo(2L);
        assertThat(m.get("processing")).isEqualTo(3L);
        assertThat(m.get("out_for_delivery")).isEqualTo(1L);
        assertThat(m.get("confirmed")).isNotEqualTo(m.get("processing"));
        assertThat(m.get("out_for_delivery")).isNotEqualTo(m.get("pending"));
        assertThat(m.get("total")).isEqualTo(13L);
    }

    @Test
    void mapper_empty_counts_all_zero_never_nulls() {
        Map<String, Object> m = mapper.orderStatistics(new EnumMap<>(StatutCommande.class));
        for (String key : new String[]{"pending", "confirmed", "processing", "out_for_delivery",
                "delivered", "canceled", "returned", "failed", "total"}) {
            assertThat(m.get(key)).as(key).isEqualTo(0L);
        }
    }

    @Test
    void endpoint_returns_200_with_expected_keys_for_owner() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/order-statistics?statistics_type=today", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        for (String key : new String[]{"pending", "confirmed", "processing", "out_for_delivery",
                "delivered", "canceled", "returned", "failed", "total"}) {
            assertThat(n.has(key)).as(key).isTrue();
        }
    }

    @Test
    void endpoint_missing_statistics_type_never_500() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/order-statistics", token);
        assertThat(r.status).isEqualTo(200);
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
