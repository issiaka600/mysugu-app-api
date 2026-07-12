package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.CodePromo;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.TypeReduction;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.CodePromoRepository;
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
 * Contrat GET /api/v3/seller/coupon/list : liste filtrée au vendeur (createdBy = owner),
 * enveloppe de pagination 6valley. Un autre vendeur ne voit que ses coupons. Plan 3h.3.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerCouponListTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired CodePromoRepository codePromoRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerAEmail = "cp-list-a-" + System.nanoTime() + "@test.mysugu";
    private final String ownerBEmail = "cp-list-b-" + System.nanoTime() + "@test.mysugu";
    private Long ownerAId, ownerBId, a1, a2, b1;

    @BeforeEach
    void seed() {
        ownerAId = newOwner(ownerAEmail);
        ownerBId = newOwner(ownerBEmail);
        a1 = newCode("LISTA1" + System.nanoTime(), ownerAId);
        a2 = newCode("LISTA2" + System.nanoTime(), ownerAId);
        b1 = newCode("LISTB1" + System.nanoTime(), ownerBId);
    }

    @AfterEach
    void cleanup() {
        codePromoRepo.findById(a1).ifPresent(codePromoRepo::delete);
        codePromoRepo.findById(a2).ifPresent(codePromoRepo::delete);
        codePromoRepo.findById(b1).ifPresent(codePromoRepo::delete);
        userRepo.findById(ownerAId).ifPresent(userRepo::delete);
        userRepo.findById(ownerBId).ifPresent(userRepo::delete);
    }

    @Test
    void list_returns_only_owner_coupons_in_envelope() throws Exception {
        String tokenA = token(ownerAEmail);
        Resp r = get("/api/v3/seller/coupon/list?limit=10&offset=0", tokenA);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("total_size").asInt()).isEqualTo(2);
        assertThat(n.has("coupons")).isTrue();
        assertThat(n.get("coupons").size()).isEqualTo(2);
        for (JsonNode c : n.get("coupons")) {
            long id = c.get("id").asLong();
            assertThat(id).isIn(a1, a2);
        }

        String tokenB = token(ownerBEmail);
        JsonNode nb = M.readTree(get("/api/v3/seller/coupon/list?limit=10&offset=0", tokenB).body);
        assertThat(nb.get("total_size").asInt()).isEqualTo(1);
        assertThat(nb.get("coupons").get(0).get("id").asLong()).isEqualTo(b1);
    }

    private Long newOwner(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("N");
        u.setPrenom("P");
        u.setRole(UserRole.RESTAURANT_OWNER);
        u.setIsActive(true);
        return userRepo.save(u).getId();
    }

    private Long newCode(String code, Long ownerId) {
        CodePromo promo = CodePromo.builder()
                .code(code)
                .description("desc " + code)
                .typeReduction(TypeReduction.POURCENTAGE)
                .valeur(new BigDecimal("10.00"))
                .usageCount(0)
                .isActive(true)
                .createdBy(userRepo.findById(ownerId).orElseThrow())
                .build();
        return codePromoRepo.save(promo).getId();
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
