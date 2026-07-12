package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.dtos.restaurant.RestaurantDashboardDTO;
import ma.mysuguclientapp.entities.Restaurant;
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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat GET /api/v3/seller/get-earning-statitics?type= (orthographe 6valley conservée) :
 * synthétise un short-series MAD à partir des buckets CA de {@link RestaurantDashboardDTO}
 * (aucun historique jour-par-jour natif — approximation documentée, spec §3.2). Clés réelles
 * ({@code seller_earn}/{@code commission_earn}, tableaux) confirmées contre
 * Tiktak-vendor-app-moso (bank_info_controller.dart::getDashboardRevenueData, 3j.0) — PAS le
 * nesting {@code series.label}/{@code series.earning} initialement supposé par le design.
 * Plan 3j.2.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerEarningStatisticsTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final SellerStatsMapper mapper = new SellerStatsMapper();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "stats-earn-" + System.nanoTime() + "@test.mysugu";
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
        r.setNom("Boutique Earn");
        r.setDescription("desc");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(userRepo.findById(ownerId).orElseThrow());
        r.setIsActive(true);
        r.setCommissionPourcentage(new BigDecimal("10.00"));
        r.setLocalisation(new ma.mysuguclientapp.entities.Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        restoId = restoRepo.save(r).getId();
    }

    @AfterEach
    void cleanup() {
        restoRepo.findById(restoId).ifPresent(restoRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    private RestaurantDashboardDTO distinctDto() {
        return RestaurantDashboardDTO.builder()
                .restaurantId(1L).restaurantNom("Boutique")
                .chiffreAffairesAujourdhui(new BigDecimal("10.00"))
                .chiffreAffairesSemaine(new BigDecimal("70.00"))
                .chiffreAffairesMois(new BigDecimal("300.00"))
                .totalChiffreAffaires(new BigDecimal("999.00"))
                .build();
    }

    @Test
    void mapper_seller_earn_series_is_the_four_ca_buckets_in_order() {
        Map<String, Object> m = mapper.earningStatistics(distinctDto(), "yearEarn", BigDecimal.ZERO);
        @SuppressWarnings("unchecked")
        List<BigDecimal> series = (List<BigDecimal>) m.get("seller_earn");
        assertThat(series).containsExactly(
                new BigDecimal("10.00"), new BigDecimal("70.00"),
                new BigDecimal("300.00"), new BigDecimal("999.00"));
    }

    @Test
    void mapper_commission_earn_series_has_four_entries_matching_seller_earn() {
        Map<String, Object> m = mapper.earningStatistics(distinctDto(), "yearEarn", new BigDecimal("10"));
        @SuppressWarnings("unchecked")
        List<BigDecimal> commission = (List<BigDecimal>) m.get("commission_earn");
        assertThat(commission).hasSize(4);
        assertThat(commission.get(0)).isEqualByComparingTo("1.00"); // 10% of 10.00
        assertThat(commission.get(3)).isEqualByComparingTo("99.90"); // 10% of 999.00
    }

    @Test
    void mapper_this_month_scalar_matches_month_bucket() {
        Map<String, Object> m = mapper.earningStatistics(distinctDto(), "this_month", BigDecimal.ZERO);
        assertThat((BigDecimal) m.get("earning")).isEqualByComparingTo("300.00");
        m = mapper.earningStatistics(distinctDto(), "MonthEarn", BigDecimal.ZERO);
        assertThat((BigDecimal) m.get("earning")).isEqualByComparingTo("300.00");
    }

    @Test
    void mapper_this_week_scalar_matches_week_bucket() {
        Map<String, Object> m = mapper.earningStatistics(distinctDto(), "this_week", BigDecimal.ZERO);
        assertThat((BigDecimal) m.get("earning")).isEqualByComparingTo("70.00");
        m = mapper.earningStatistics(distinctDto(), "WeekEarn", BigDecimal.ZERO);
        assertThat((BigDecimal) m.get("earning")).isEqualByComparingTo("70.00");
    }

    @Test
    void mapper_this_year_and_total_earning_use_overall_ca_approximation() {
        Map<String, Object> m = mapper.earningStatistics(distinctDto(), "this_year", BigDecimal.ZERO);
        assertThat((BigDecimal) m.get("earning")).isEqualByComparingTo("999.00");
        assertThat((BigDecimal) m.get("this_year")).isEqualByComparingTo("999.00");
        assertThat((BigDecimal) m.get("total_earning")).isEqualByComparingTo("999.00");
    }

    @Test
    void mapper_unknown_type_falls_back_to_today_never_crashes() {
        Map<String, Object> m = mapper.earningStatistics(distinctDto(), "bogus", BigDecimal.ZERO);
        assertThat((BigDecimal) m.get("earning")).isEqualByComparingTo("10.00");
    }

    @Test
    void mapper_amounts_are_mad_pass_through_no_conversion() {
        Map<String, Object> m = mapper.earningStatistics(distinctDto(), "today", BigDecimal.ZERO);
        assertThat((BigDecimal) m.get("total_earning")).isEqualByComparingTo("999.00");
    }

    @Test
    void mapper_never_nulls_numeric_fields() {
        RestaurantDashboardDTO empty = RestaurantDashboardDTO.builder().build();
        Map<String, Object> m = mapper.earningStatistics(empty, "today", null);
        for (String key : new String[]{"total_earning", "this_year", "commission_earning", "earning"}) {
            assertThat(m.get(key)).as(key).isNotNull();
        }
        assertThat(m.get("seller_earn")).isNotNull();
        assertThat(m.get("commission_earn")).isNotNull();
    }

    @Test
    void endpoint_returns_200_with_expected_keys_for_owner() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/get-earning-statitics?type=yearEarn", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        for (String key : new String[]{"total_earning", "this_year", "commission_earning",
                "earning", "seller_earn", "commission_earn"}) {
            assertThat(n.has(key)).as(key).isTrue();
        }
        assertThat(n.get("seller_earn").size()).isEqualTo(4);
    }

    @Test
    void endpoint_missing_type_never_500() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/get-earning-statitics", token);
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
