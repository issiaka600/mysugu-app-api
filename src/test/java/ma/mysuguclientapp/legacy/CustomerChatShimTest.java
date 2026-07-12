package ma.mysuguclientapp.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.EmailService;
import ma.mysuguclientapp.services.implementations.MinioService;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Contrat de la nouvelle façade chat client (/api/v1/customer/chat/*) posée sur le store unifié
 * (Task 7). Mêmes conventions que {@link DeliveryManChatShimTest} : vrai HTTP (JDK HttpClient)
 * contre le Postgres de test, dépendances externes mockées. Se distingue de la façade livreur par
 * des flags {@code sent_by_*} ENTIERS (1/0), pas booléens — contrat consommé par l'app MySuKu.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class CustomerChatShimTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private Long customerId;
    private Long livreurId;

    @BeforeEach
    void seed() throws Exception {
        when(minioService.uploadFile(any(), anyString())).thenReturn("https://test/chat-image.png");
        jdbc.execute("DO $$ DECLARE r RECORD; BEGIN " +
                "FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname='public') LOOP " +
                "EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' RESTART IDENTITY CASCADE'; " +
                "END LOOP; END $$;");

        User customer = newUser("customer.chat@mysugu.local", "Chat", "Client", UserRole.CLIENT);
        customer.setTelephone("+212600000010");
        customer.setCountryCode("+212");
        customerId = userRepo.save(customer).getId();

        User livreur = newUser("livreur.chat.customer@mysugu.local", "Test", "Livreur", UserRole.LIVREUR);
        livreur.setTelephone("+212600000011");
        livreur.setCountryCode("+212");
        livreurId = userRepo.save(livreur).getId();
    }

    private User newUser(String email, String nom, String prenom, UserRole role) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(encoder.encode("demo1234"));
        u.setNom(nom);
        u.setPrenom(prenom);
        u.setRole(role);
        u.setIsActive(true);
        return u;
    }

    // ---------- helpers ----------

    private record Resp(int status, String body) {}

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Resp doGet(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(path))).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp doPostJson(String path, Map<String, Object> body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body)));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp sendMessage(String token, String type, Long recipientId, String message) throws Exception {
        String boundary = "----customerchatshimboundary";
        String CRLF = "\r\n";
        String body = "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"message\"" + CRLF + CRLF
                + message + CRLF
                + "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"id\"" + CRLF + CRLF
                + recipientId + CRLF
                + "--" + boundary + "--" + CRLF;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url("/api/v1/customer/chat/send-message/" + type)))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp seenMessage(String token, String type, Long id) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(url("/api/v1/customer/chat/seen-message/" + type + "?id=" + id)))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private String loginCustomer() throws Exception {
        JsonNode n = M.readTree(doPostJson("/auth/login",
                Map.of("email", "customer.chat@mysugu.local", "password", "demo1234"), null).body());
        return n.hasNonNull("token") ? n.get("token").asText() : null;
    }

    // ---------- tests ----------

    @Test
    void send_message_to_delivery_man_persists_and_returns_200() throws Exception {
        String t = loginCustomer();
        assertThat(t).isNotBlank();

        Resp r = sendMessage(t, "delivery-man", livreurId, "hi");
        assertThat(r.status()).isEqualTo(200);
        JsonNode n = M.readTree(r.body());
        assertThat(n.get("message").asText()).isEqualTo("hi");
        assertThat(n.get("time").asText()).isNotBlank();
        assertThat(n.get("image").isArray()).isTrue();
    }

    @Test
    void get_messages_returns_integer_sent_by_customer_flag() throws Exception {
        String t = loginCustomer();
        assertThat(sendMessage(t, "delivery-man", livreurId, "hi").status()).isEqualTo(200);

        Resp r = doGet("/api/v1/customer/chat/get-messages/delivery-man/" + livreurId, t);
        JsonNode n = M.readTree(r.body());
        assertThat(n.get("message").isArray()).isTrue();
        assertThat(n.get("message")).hasSize(1);
        JsonNode msg = n.get("message").get(0);
        assertThat(msg.get("message").asText()).isEqualTo("hi");

        // Must be JSON integers 1/0, NOT booleans — MySuKu app asserts `== 1`.
        assertThat(msg.get("sent_by_customer").isIntegralNumber())
                .as("sent_by_customer must serialize as a JSON integer, not a boolean")
                .isTrue();
        assertThat(msg.get("sent_by_customer").asInt()).isEqualTo(1);
        assertThat(msg.get("sent_by_seller").asInt()).isEqualTo(0);
        assertThat(msg.get("sent_by_admin").asInt()).isEqualTo(0);
        // Belt-and-braces: the raw wire form must be the literal `1`, not `true`.
        assertThat(r.body()).contains("\"sent_by_customer\":1");
    }

    @Test
    void list_delivery_man_reports_conversation_and_unseen_count() throws Exception {
        String t = loginCustomer();
        assertThat(sendMessage(t, "delivery-man", livreurId, "hello").status()).isEqualTo(200);

        JsonNode n = M.readTree(doGet("/api/v1/customer/chat/list/delivery-man", t).body());
        assertThat(n.get("chat").isArray()).isTrue();
        assertThat(n.get("chat")).hasSize(1);
        JsonNode row = n.get("chat").get(0);
        assertThat(row.get("delivery_man_id").asLong()).isEqualTo(livreurId);
        assertThat(row.get("deliveryMan")).isNotNull();
        assertThat(row.has("unseen_message_count")).isTrue();
    }

    @Test
    void seen_message_returns_200() throws Exception {
        String t = loginCustomer();
        assertThat(sendMessage(t, "delivery-man", livreurId, "hi").status()).isEqualTo(200);

        Resp r = seenMessage(t, "delivery-man", livreurId);
        assertThat(r.status()).isEqualTo(200);
    }

    @Test
    void seller_type_with_id_zero_routes_to_admin_without_crashing() throws Exception {
        String t = loginCustomer();

        Resp r = doGet("/api/v1/customer/chat/get-messages/seller/0", t);
        assertThat(r.status()).isEqualTo(200);
        JsonNode n = M.readTree(r.body());
        assertThat(n.get("message").isArray()).isTrue();
        assertThat(n.get("message")).isEmpty();
    }
}
