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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat check-coupon du shim vendeur : délègue à CodePromoService.validerEtCalculer et mappe
 * ResultatCodePromoDTO -> JSON discount 6valley (coupon_discount_amount). L'app POST le body
 * {code,user_id,order_amount} (cart_repository.dart). Code invalide -> forme bénigne (pas 500).
 * Plan 3h.5.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerCouponCheckTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired CodePromoRepository codePromoRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "cp-check-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, couponId;
    private String couponCode;

    @BeforeEach
    void seed() {
        ownerId = newOwner(ownerEmail);
        couponCode = "CHECK" + System.nanoTime();
        couponId = newActiveCode(couponCode, ownerId);
    }

    @AfterEach
    void cleanup() {
        codePromoRepo.findById(couponId).ifPresent(codePromoRepo::delete);
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
    }

    @Test
    void check_valid_coupon_returns_discount_amount() throws Exception {
        String token = token(ownerEmail);
        Map<String, Object> body = new HashMap<>();
        body.put("code", couponCode);
        body.put("user_id", 0);
        body.put("order_amount", 100);
        Resp r = postJson("/api/v3/seller/coupon/check-coupon", token, body);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("coupon_discount_amount").asDouble()).isEqualTo(10.0);
        assertThat(n.get("is_valid").asBoolean()).isTrue();
    }

    @Test
    void check_invalid_coupon_returns_benign_shape_not_500() throws Exception {
        String token = token(ownerEmail);
        Map<String, Object> body = new HashMap<>();
        body.put("code", "NOPE" + System.nanoTime());
        body.put("user_id", 0);
        body.put("order_amount", 100);
        Resp r = postJson("/api/v3/seller/coupon/check-coupon", token, body);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("coupon_discount_amount").asDouble()).isEqualTo(0.0);
        assertThat(n.get("is_valid").asBoolean()).isFalse();
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

    private Long newActiveCode(String code, Long ownerId) {
        CodePromo promo = CodePromo.builder()
                .code(code.toUpperCase())
                .description("desc " + code)
                .typeReduction(TypeReduction.POURCENTAGE)
                .valeur(new BigDecimal("10.00"))
                .montantMinCommande(BigDecimal.ZERO)
                .dateDebut(LocalDateTime.now().minusDays(1))
                .dateFin(LocalDateTime.now().plusDays(30))
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
