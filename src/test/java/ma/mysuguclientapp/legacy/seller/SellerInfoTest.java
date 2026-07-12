package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
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
 * Contrat seller-info du shim vendeur : GET /api/v3/seller/seller-info renvoie l'objet
 * "seller" 6valley (id,f_name,l_name,phone,email,image,status) pour le RESTAURANT_OWNER
 * authentifié, en réutilisant UserService.getProfile. Un non-vendeur -> 403.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerInfoTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private final String ownerEmail = "seller-info-" + System.nanoTime() + "@test.mysugu";
    private final String clientEmail = "seller-info-client-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId;
    private Long clientId;

    @BeforeEach
    void seed() {
        User owner = new User();
        owner.setEmail(ownerEmail);
        owner.setPassword(encoder.encode("demo1234"));
        owner.setNom("Bar"); owner.setPrenom("Foo");
        owner.setTelephone("+212600001111");
        owner.setAvatar("avatars/foo.png");
        owner.setRole(UserRole.RESTAURANT_OWNER);
        owner.setIsActive(true);
        ownerId = userRepo.save(owner).getId();

        User client = new User();
        client.setEmail(clientEmail);
        client.setPassword(encoder.encode("demo1234"));
        client.setNom("C"); client.setPrenom("D");
        client.setRole(UserRole.CLIENT);
        client.setIsActive(true);
        clientId = userRepo.save(client).getId();
    }

    @AfterEach
    void cleanup() {
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
        userRepo.findById(clientId).ifPresent(userRepo::delete);
    }

    @Test
    void seller_info_returns_6valley_seller_object() throws Exception {
        String t = ownerToken();
        Resp r = get("/api/v3/seller/seller-info", t);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("f_name").asText()).isEqualTo("Foo");
        assertThat(n.get("l_name").asText()).isEqualTo("Bar");
        assertThat(n.get("email").asText()).isEqualTo(ownerEmail);
        assertThat(n.get("image").asText()).isEqualTo("avatars/foo.png");
        assertThat(n.has("phone")).isTrue();
        assertThat(n.has("status")).isTrue();
        // Champ non-null obligatoire côté app (ProfileInfoModel.fromJson lit pos_status sans garde).
        assertThat(n.has("pos_status")).isTrue();
    }

    @Test
    void seller_info_rejects_non_owner() throws Exception {
        String t = jwtTokenProvider.generateToken(userRepo.findById(clientId).orElseThrow());
        Resp r = get("/api/v3/seller/seller-info", t);
        assertThat(r.status).isEqualTo(403);
    }

    private String ownerToken() throws Exception {
        Resp r = post("/api/v3/seller/auth/login", Map.of("email", ownerEmail, "password", "demo1234"), null);
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

    private Resp get(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
