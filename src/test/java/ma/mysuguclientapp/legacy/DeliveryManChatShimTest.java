package ma.mysuguclientapp.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.*;
import ma.mysuguclientapp.services.chat.ConversationService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Contrat du shim chat livreur (/api/v2/delivery-man/messages/*) rebranché sur le store unifié
 * (Task 5). Mêmes conventions que {@link DeliveryManShimContractTest} : vrai HTTP (JDK
 * HttpClient) contre le Postgres de test, dépendances externes mockées.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class DeliveryManChatShimTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ConversationService chat;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private Long livreurId;
    private Long customerId;

    @BeforeEach
    void seed() throws Exception {
        when(minioService.uploadFile(any(), anyString())).thenReturn("https://test/chat-image.png");
        jdbc.execute("DO $$ DECLARE r RECORD; BEGIN " +
                "FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname='public') LOOP " +
                "EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' RESTART IDENTITY CASCADE'; " +
                "END LOOP; END $$;");

        User livreur = newUser("livreur.chat@mysugu.local", "Test", "Livreur", UserRole.LIVREUR);
        livreur.setTelephone("+212600000002");
        livreur.setCountryCode("+212");
        livreurId = userRepo.save(livreur).getId();

        User customer = newUser("customer.chat@mysugu.local", "Client", "Chat", UserRole.CLIENT);
        customerId = userRepo.save(customer).getId();
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

    private Resp doPostJson(String path, java.util.Map<String, Object> body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body)));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp sendMessage(String token, String type, Long recipientId, String message) throws Exception {
        String boundary = "----chatshimboundary";
        String CRLF = "\r\n";
        String body = "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"message\"" + CRLF + CRLF
                + message + CRLF
                + "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"id\"" + CRLF + CRLF
                + recipientId + CRLF
                + "--" + boundary + "--" + CRLF;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url("/api/v2/delivery-man/messages/send-message/" + type)))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private String loginLivreur() throws Exception {
        JsonNode n = M.readTree(doPostJson("/api/v2/delivery-man/auth/login",
                java.util.Map.of("country_code", "+212", "phone", "+212600000002", "password", "demo1234"), null).body());
        return n.hasNonNull("token") ? n.get("token").asText() : null;
    }

    // ---------- tests ----------

    @Test
    void send_message_persists_via_unified_conversation_service() throws Exception {
        String t = loginLivreur();
        assertThat(t).isNotBlank();

        Resp r = sendMessage(t, "customer", customerId, "hi");
        assertThat(r.status()).isEqualTo(200);
        JsonNode n = M.readTree(r.body());
        assertThat(n.get("message").asText()).isEqualTo("hi");
        assertThat(n.get("time").asText()).isNotBlank();
        assertThat(n.get("image").isArray()).isTrue();

        // Persisted in the UNIFIED store, not the legacy MessageLivreur table.
        var thread = chat.thread(new ParticipantRef(ParticipantType.LIVREUR, livreurId),
                new ParticipantRef(ParticipantType.CUSTOMER, customerId));
        assertThat(thread).hasSize(1);
        assertThat(thread.get(0).getContenu()).isEqualTo("hi");
        assertThat(thread.get(0).getExpediteurType()).isEqualTo(ParticipantType.LIVREUR);
        assertThat(thread.get(0).getExpediteurId()).isEqualTo(livreurId);
    }

    @Test
    void get_message_returns_thread_with_livreur_sent_flags_true() throws Exception {
        String t = loginLivreur();
        assertThat(sendMessage(t, "customer", customerId, "hi").status()).isEqualTo(200);

        JsonNode n = M.readTree(doGet("/api/v2/delivery-man/messages/get-message/customer/" + customerId, t).body());
        assertThat(n.get("message").isArray()).isTrue();
        assertThat(n.get("message")).hasSize(1);
        JsonNode msg = n.get("message").get(0);
        assertThat(msg.get("message").asText()).isEqualTo("hi");
        // Livreur is the sender in this app's shape => sent_by_* all false for the message he wrote,
        // and seen_by_delivery_man is true (his own message).
        assertThat(msg.get("sent_by_customer").asBoolean()).isFalse();
        assertThat(msg.get("sent_by_seller").asBoolean()).isFalse();
        assertThat(msg.get("sent_by_admin").asBoolean()).isFalse();
        assertThat(msg.get("seen_by_delivery_man").asBoolean()).isTrue();
        assertThat(msg.get("attachment").isArray()).isTrue();
    }

    @Test
    void get_message_marks_customer_authored_message_as_seen() throws Exception {
        String t = loginLivreur();
        ParticipantRef customerRef = new ParticipantRef(ParticipantType.CUSTOMER, customerId);
        ParticipantRef livreurRef = new ParticipantRef(ParticipantType.LIVREUR, livreurId);
        // Customer writes to livreur directly through the unified service (simulating the
        // customer-side chat façade, which is out of scope here).
        chat.append(customerRef, livreurRef, "salut", java.util.List.of());

        var conv = chat.conversationsFor(livreurRef, ParticipantType.CUSTOMER).get(0);
        assertThat(chat.unseenCount(conv, livreurRef)).isEqualTo(1);

        JsonNode n = M.readTree(doGet("/api/v2/delivery-man/messages/get-message/customer/" + customerId, t).body());
        JsonNode msg = n.get("message").get(0);
        assertThat(msg.get("message").asText()).isEqualTo("salut");
        assertThat(msg.get("sent_by_customer").asBoolean()).isTrue();
        assertThat(msg.get("seen_by_delivery_man").asBoolean()).isTrue();

        assertThat(chat.unseenCount(conv, livreurRef)).isEqualTo(0);
    }

    @Test
    void list_groups_conversations_by_counterpart_and_reports_unseen_count() throws Exception {
        String t = loginLivreur();
        assertThat(sendMessage(t, "customer", customerId, "hello").status()).isEqualTo(200);
        chat.append(new ParticipantRef(ParticipantType.CUSTOMER, customerId),
                new ParticipantRef(ParticipantType.LIVREUR, livreurId), "reply", java.util.List.of());

        JsonNode n = M.readTree(doGet("/api/v2/delivery-man/messages/list/customer", t).body());
        assertThat(n.get("chat").isArray()).isTrue();
        assertThat(n.get("chat")).hasSize(1);
        JsonNode row = n.get("chat").get(0);
        assertThat(row.get("user_id").asLong()).isEqualTo(customerId);
        assertThat(row.get("message").asText()).isEqualTo("reply");
        assertThat(row.get("sent_by_customer").asBoolean()).isTrue();
        assertThat(row.get("unseen_message_count").asInt()).isEqualTo(1);
        assertThat(row.get("customer")).isNotNull();
        assertThat(row.get("seller_info")).isNotNull();
        assertThat(n.get("total_size").asInt()).isEqualTo(1);
    }
}
