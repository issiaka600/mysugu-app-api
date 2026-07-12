package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.CodePromo;
import ma.mysuguclientapp.entities.User;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat POST /api/v3/seller/coupon/store (shim vendeur) : crée un CodePromo scopé au
 * vendeur authentifié (createdBy = owner). Voir plan 3h.2, spec 2026-07-10-vendor-3h-coupons-design.md §2/§5.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerCouponStoreTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired CodePromoRepository codePromoRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "cp-store-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId;
    private Long createdCodeId;

    @BeforeEach
    void seed() {
        ownerId = newOwner(ownerEmail);
    }

    @AfterEach
    void cleanup() {
        if (createdCodeId != null) {
            codePromoRepo.findById(createdCodeId).ifPresent(codePromoRepo::delete);
        }
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    @Test
    void store_creates_coupon_scoped_to_owner() throws Exception {
        String token = token(ownerEmail);
        String code = "WELCOME" + System.nanoTime();

        Resp r = postJson("/api/v3/seller/coupon/store", token, Map.of(
                "title", "Bienvenue",
                "coupon_type", "discount_on_purchase",
                "code", code,
                "limit", 5,
                "discount_type", "percentage",
                "discount", 10,
                "min_purchase", 20,
                "max_discount", 15,
                "start_date", "2026-01-01",
                "expire_date", "2026-12-31"
        ));
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("code").asText()).isEqualTo(code.toUpperCase());
        assertThat(n.get("discount_type").asText()).isEqualTo("percentage");
        assertThat(n.get("discount").asDouble()).isEqualTo(10.0);
        assertThat(n.get("status").asInt()).isEqualTo(1);
        assertThat(n.get("id")).isNotNull();

        createdCodeId = n.get("id").asLong();
        CodePromo persisted = codePromoRepo.findById(createdCodeId).orElseThrow();
        assertThat(persisted.getCreatedBy()).isNotNull();
        assertThat(persisted.getCreatedBy().getId()).isEqualTo(ownerId);
        assertThat(persisted.getIsActive()).isTrue();
        assertThat(persisted.getUsageCount()).isZero();
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

    private record Resp(int status, String body) {}
}
