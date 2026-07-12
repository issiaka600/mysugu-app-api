package ma.mysuguclientapp.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du shim d'authentification client 6valley-compatible ({@code /api/v1/auth/*}) consommé
 * par l'app MySuKu — elle ne change que sa base URL, donc login/register doivent renvoyer un
 * simple {@code {token}} (jamais {@code temporary_token}) et des erreurs au format
 * {@code {errors:[{code,message}]}}. Même style que {@link CustomerChatShimTest} : vrai HTTP (JDK
 * HttpClient) contre le Postgres de test.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CustomerAuthShimTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        jdbc.execute("DO $$ DECLARE r RECORD; BEGIN " +
                "FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname='public') LOOP " +
                "EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' RESTART IDENTITY CASCADE'; " +
                "END LOOP; END $$;");

        User customer = new User();
        customer.setEmail("c@x.com");
        customer.setPassword(encoder.encode("secret123"));
        customer.setNom("X");
        customer.setPrenom("C");
        customer.setTelephone("+212600000099");
        customer.setRole(UserRole.CLIENT);
        customer.setIsActive(true);
        userRepo.save(customer);
    }

    // ---------- helpers ----------

    private record Resp(int status, String body) {}

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Resp doPostJson(String path, Map<String, Object> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body)))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    // ---------- tests ----------

    @Test
    void login_returns_token_and_never_temporary_token() throws Exception {
        Resp r = doPostJson("/api/v1/auth/login",
                Map.of("email", "c@x.com", "password", "secret123", "guest_id", "0"));
        assertThat(r.status()).isEqualTo(200);
        JsonNode n = M.readTree(r.body());
        assertThat(n.hasNonNull("token")).isTrue();
        assertThat(n.has("temporary_token")).isFalse();
    }

    @Test
    void login_with_wrong_password_returns_401_with_errors() throws Exception {
        Resp r = doPostJson("/api/v1/auth/login",
                Map.of("email", "c@x.com", "password", "wrong-password", "guest_id", "0"));
        assertThat(r.status()).isEqualTo(401);
        JsonNode n = M.readTree(r.body());
        assertThat(n.has("errors")).isTrue();
    }

    @Test
    void register_creates_client_and_returns_token() throws Exception {
        Resp r = doPostJson("/api/v1/auth/register", Map.of(
                "f_name", "New",
                "l_name", "Client",
                "email", "n@x.com",
                "phone", "+212600000098",
                "password", "secret123"));
        assertThat(r.status()).isEqualTo(200);
        JsonNode n = M.readTree(r.body());
        assertThat(n.hasNonNull("token")).isTrue();

        User created = userRepo.findByEmail("n@x.com").orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.CLIENT);
        assertThat(created.getIsActive()).isTrue();
    }
}
