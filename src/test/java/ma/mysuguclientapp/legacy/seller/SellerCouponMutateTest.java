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
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat update/{id} + delete/{id} + status-update/{id} du shim vendeur, CHACUN avec la garde
 * d'appartenance (coupon.createdBy == vendeur courant, sinon 404). Test cross-vendeur : deux
 * vendeurs chacun avec un coupon, agir sur celui de l'autre -> 404, sans mutation. Plan 3h.4.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerCouponMutateTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired CodePromoRepository codePromoRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerAEmail = "cp-mut-a-" + System.nanoTime() + "@test.mysugu";
    private final String ownerBEmail = "cp-mut-b-" + System.nanoTime() + "@test.mysugu";
    private Long ownerAId, ownerBId, couponA, couponB;

    @BeforeEach
    void seed() {
        ownerAId = newOwner(ownerAEmail);
        ownerBId = newOwner(ownerBEmail);
        couponA = newCode("MUTA" + System.nanoTime(), ownerAId);
        couponB = newCode("MUTB" + System.nanoTime(), ownerBId);
    }

    @AfterEach
    void cleanup() {
        codePromoRepo.findById(couponA).ifPresent(codePromoRepo::delete);
        codePromoRepo.findById(couponB).ifPresent(codePromoRepo::delete);
        userRepo.findById(ownerAId).ifPresent(userRepo::delete);
        userRepo.findById(ownerBId).ifPresent(userRepo::delete);
    }

    @Test
    void owner_updates_own_coupon() throws Exception {
        String tokenA = token(ownerAEmail);
        Map<String, Object> form = new HashMap<>();
        form.put("title", "MàJ titre");
        form.put("code", codePromoRepo.findById(couponA).orElseThrow().getCode());
        form.put("discount_type", "amount");
        form.put("discount", 25);
        form.put("min_purchase", 40);
        form.put("max_discount", 0);
        form.put("limit", 9);
        form.put("_method", "put");
        Resp r = postJson("/api/v3/seller/coupon/update/" + couponA, tokenA, form);
        assertThat(r.status).isEqualTo(200);

        CodePromo updated = codePromoRepo.findById(couponA).orElseThrow();
        assertThat(updated.getValeur()).isEqualByComparingTo("25");
        assertThat(updated.getDescription()).isEqualTo("MàJ titre");
        assertThat(updated.getUsageMax()).isEqualTo(9);
    }

    @Test
    void owner_toggles_status() throws Exception {
        String tokenA = token(ownerAEmail);
        Resp off = postJson("/api/v3/seller/coupon/status-update/" + couponA, tokenA, Map.of("status", 0));
        assertThat(off.status).isEqualTo(200);
        assertThat(codePromoRepo.findById(couponA).orElseThrow().getIsActive()).isFalse();

        Resp on = postJson("/api/v3/seller/coupon/status-update/" + couponA, tokenA, Map.of("status", 1));
        assertThat(on.status).isEqualTo(200);
        assertThat(codePromoRepo.findById(couponA).orElseThrow().getIsActive()).isTrue();
    }

    @Test
    void owner_deletes_own_coupon() throws Exception {
        String tokenA = token(ownerAEmail);
        Resp r = deleteReq("/api/v3/seller/coupon/delete/" + couponA, tokenA);
        assertThat(r.status).isEqualTo(200);
        assertThat(codePromoRepo.findById(couponA)).isEmpty();
    }

    @Test
    void mutating_another_owners_coupon_returns_404_and_does_not_change_it() throws Exception {
        String tokenA = token(ownerAEmail);

        // update B's coupon as A -> 404
        Map<String, Object> form = new HashMap<>();
        form.put("title", "hack");
        form.put("code", codePromoRepo.findById(couponB).orElseThrow().getCode());
        form.put("discount_type", "amount");
        form.put("discount", 99);
        Resp upd = postJson("/api/v3/seller/coupon/update/" + couponB, tokenA, form);
        assertThat(upd.status).isEqualTo(404);

        // status-update B's coupon as A -> 404
        Resp st = postJson("/api/v3/seller/coupon/status-update/" + couponB, tokenA, Map.of("status", 0));
        assertThat(st.status).isEqualTo(404);

        // delete B's coupon as A -> 404
        Resp del = deleteReq("/api/v3/seller/coupon/delete/" + couponB, tokenA);
        assertThat(del.status).isEqualTo(404);

        CodePromo b = codePromoRepo.findById(couponB).orElseThrow();
        assertThat(b.getIsActive()).isTrue();
        assertThat(b.getValeur()).isEqualByComparingTo("10.00");
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

    private Resp postJson(String path, String token, Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body))).build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp deleteReq(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).DELETE().build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
