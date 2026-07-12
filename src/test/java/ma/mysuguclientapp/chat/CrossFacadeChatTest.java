package ma.mysuguclientapp.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.ConversationUnifieeRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Cross-façade integration test (Task 9): proves the LIVREUR façade (Task 5,
 * {@code /api/v2/delivery-man/messages/*}), the CUSTOMER façade (Task 7,
 * {@code /api/v1/customer/chat/*}) and the seller channel (Task 6, {@code /api/messages/*}) all
 * read/write the SAME {@link ma.mysuguclientapp.services.chat.ConversationService}-backed store —
 * a message written through one façade is visible through another for the same participant pair,
 * and there is exactly one {@link ma.mysuguclientapp.entities.ConversationUnifiee} row per pair.
 * Same conventions as {@code DeliveryManChatShimTest}/{@code CustomerChatShimTest}: real HTTP (JDK
 * HttpClient) against the test Postgres, external dependencies mocked.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class CrossFacadeChatTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restaurantRepo;
    @Autowired ConversationUnifieeRepository conversationUnifieeRepo;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private Long customerId;
    private Long livreurId;
    private Long ownerId;
    private Long restaurantId;

    @BeforeEach
    void seed() throws Exception {
        when(minioService.uploadFile(any(), anyString())).thenReturn("https://test/chat-image.png");
        jdbc.execute("DO $$ DECLARE r RECORD; BEGIN " +
                "FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname='public') LOOP " +
                "EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' RESTART IDENTITY CASCADE'; " +
                "END LOOP; END $$;");

        User customer = newUser("customer.crossfacade@mysugu.local", "Chat", "Client", UserRole.CLIENT);
        customer.setTelephone("+212600000020");
        customer.setCountryCode("+212");
        customerId = userRepo.save(customer).getId();

        User livreur = newUser("livreur.crossfacade@mysugu.local", "Test", "Livreur", UserRole.LIVREUR);
        livreur.setTelephone("+212600000021");
        livreur.setCountryCode("+212");
        livreurId = userRepo.save(livreur).getId();

        User owner = newUser("owner.crossfacade@mysugu.local", "Resto", "Owner", UserRole.RESTAURANT_OWNER);
        owner.setTelephone("+212600000022");
        owner.setCountryCode("+212");
        ownerId = userRepo.save(owner).getId();

        Restaurant restaurant = new Restaurant();
        restaurant.setNom("Chez Cross-Façade");
        restaurant.setOwner(owner);
        restaurantId = restaurantRepo.save(restaurant).getId();
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

    // ---------- HTTP helpers ----------

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

    /** Multipart send used by both the livreur façade and the customer façade (same shape). */
    private Resp sendMultipart(String basePath, String token, String type, Long recipientId, String message) throws Exception {
        String boundary = "----crossfacadeboundary";
        String CRLF = "\r\n";
        String body = "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"message\"" + CRLF + CRLF
                + message + CRLF
                + "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"id\"" + CRLF + CRLF
                + recipientId + CRLF
                + "--" + boundary + "--" + CRLF;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url(basePath + "/send-message/" + type)))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private String login(String email) throws Exception {
        JsonNode n = M.readTree(doPostJson("/auth/login", Map.of("email", email, "password", "demo1234"), null).body());
        return n.hasNonNull("token") ? n.get("token").asText() : null;
    }

    // ---------- scenarios ----------

    /**
     * Scenario A (customer <-> livreur): a message written through the CUSTOMER façade
     * (/api/v1/customer/chat) must be readable through the LIVREUR façade
     * (/api/v2/delivery-man/messages) for the SAME participant pair, and vice versa — proving both
     * façades share one store. Uses the RIGHT flag shape per façade: livreur façade returns
     * BOOLEAN sent_by_customer (relative: false for the recipient's own message), customer façade
     * returns INTEGER 1/0 (absolute: reflects the true sender type).
     */
    @Test
    void customer_and_livreur_facades_share_one_conversation() throws Exception {
        String customerToken = login("customer.crossfacade@mysugu.local");
        String livreurToken = login("livreur.crossfacade@mysugu.local");
        assertThat(customerToken).isNotBlank();
        assertThat(livreurToken).isNotBlank();

        // Customer -> livreur via the CUSTOMER façade.
        Resp sendHi = sendMultipart("/api/v1/customer/chat", customerToken, "delivery-man", livreurId, "hi");
        assertThat(sendHi.status()).isEqualTo(200);

        // Livreur reads it via the LIVREUR façade — same store, same thread.
        JsonNode livreurThread = M.readTree(
                doGet("/api/v2/delivery-man/messages/get-message/customer/" + customerId, livreurToken).body());
        assertThat(livreurThread.get("message")).hasSize(1);
        JsonNode hiFromLivreurSide = livreurThread.get("message").get(0);
        assertThat(hiFromLivreurSide.get("message").asText()).isEqualTo("hi");
        // Livreur is the reader here, customer authored it => sent_by_customer BOOLEAN true.
        assertThat(hiFromLivreurSide.get("sent_by_customer").isBoolean()).isTrue();
        assertThat(hiFromLivreurSide.get("sent_by_customer").asBoolean()).isTrue();

        // Livreur replies via the LIVREUR façade.
        Resp sendRe = sendMultipart("/api/v2/delivery-man/messages", livreurToken, "customer", customerId, "re");
        assertThat(sendRe.status()).isEqualTo(200);

        // Customer reads both messages via the CUSTOMER façade — same store, same thread.
        JsonNode customerThread = M.readTree(
                doGet("/api/v1/customer/chat/get-messages/delivery-man/" + livreurId, customerToken).body());
        assertThat(customerThread.get("message")).hasSize(2);
        List<String> texts = new java.util.ArrayList<>();
        customerThread.get("message").forEach(m -> texts.add(m.get("message").asText()));
        assertThat(texts).containsExactly("hi", "re");

        JsonNode reFromCustomerSide = customerThread.get("message").get(1);
        assertThat(reFromCustomerSide.get("message").asText()).isEqualTo("re");
        // Customer façade flags are INTEGER 1/0 and reflect the TRUE sender type (absolute, not
        // relative to the reader) — "re" was authored by the livreur, so sent_by_customer == 0.
        assertThat(reFromCustomerSide.get("sent_by_customer").isIntegralNumber()).isTrue();
        assertThat(reFromCustomerSide.get("sent_by_customer").asInt()).isEqualTo(0);

        JsonNode hiFromCustomerSide = customerThread.get("message").get(0);
        assertThat(hiFromCustomerSide.get("sent_by_customer").asInt()).isEqualTo(1);

        // Exactly one conversation row for the (LIVREUR, CUSTOMER) pair.
        long rows = conversationUnifieeRepo.findByParticipant(ParticipantType.LIVREUR, livreurId).stream()
                .filter(c -> (c.getPartyAType() == ParticipantType.CUSTOMER && c.getPartyAId().equals(customerId))
                        || (c.getPartyBType() == ParticipantType.CUSTOMER && c.getPartyBId().equals(customerId)))
                .count();
        assertThat(rows).isEqualTo(1);
    }

    /**
     * Scenario B (customer <-> restaurant, crosses with /api/messages): a message written by the
     * customer through the CUSTOMER façade's "seller" type must be visible to the restaurant owner
     * through the pre-existing seller channel (/api/messages/*), and there must be exactly one
     * {@link ma.mysuguclientapp.entities.ConversationUnifiee} row for the pair — proving both
     * entry points delegate to the same unified store.
     */
    @Test
    void customer_and_restaurant_seller_channel_share_one_conversation_row() throws Exception {
        String customerToken = login("customer.crossfacade@mysugu.local");
        String ownerToken = login("owner.crossfacade@mysugu.local");
        assertThat(customerToken).isNotBlank();
        assertThat(ownerToken).isNotBlank();

        // Customer -> restaurant via the CUSTOMER façade ("seller" type -> RESTAURANT participant).
        Resp sendYo = sendMultipart("/api/v1/customer/chat", customerToken, "seller", restaurantId, "yo");
        assertThat(sendYo.status()).isEqualTo(200);

        // Restaurant owner lists conversations via the seller channel (/api/messages/conversations)
        // — same store, must see the thread with this client.
        Resp listResp = doGet("/api/messages/conversations", ownerToken);
        assertThat(listResp.status()).isEqualTo(200);
        JsonNode conversations = M.readTree(listResp.body());
        assertThat(conversations.isArray()).isTrue();
        assertThat(conversations).hasSize(1);
        JsonNode conv = conversations.get(0);
        assertThat(conv.get("clientId").asLong()).isEqualTo(customerId);
        assertThat(conv.get("restaurantId").asLong()).isEqualTo(restaurantId);
        assertThat(conv.get("dernierMessage").asText()).isEqualTo("yo");
        long conversationId = conv.get("id").asLong();

        // Owner reads the messages of that conversation via the seller channel.
        Resp msgsResp = doGet("/api/messages/conversations/" + conversationId, ownerToken);
        assertThat(msgsResp.status()).isEqualTo(200);
        JsonNode msgs = M.readTree(msgsResp.body());
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).get("contenu").asText()).isEqualTo("yo");
        // The owner did not author it => envoyeParMoi is false.
        assertThat(msgs.get(0).get("envoyeParMoi").asBoolean()).isFalse();

        // Owner replies through the seller channel (JSON POST /api/messages).
        Resp reply = doPostJson("/api/messages",
                Map.of("conversationId", conversationId, "contenu", "bonjour"), ownerToken);
        assertThat(reply.status()).isEqualTo(201);

        // Customer reads the reply back through the CUSTOMER façade — proves the seller channel's
        // write landed in the same store the customer façade reads from.
        JsonNode customerThread = M.readTree(
                doGet("/api/v1/customer/chat/get-messages/seller/" + restaurantId, customerToken).body());
        assertThat(customerThread.get("message")).hasSize(2);
        List<String> texts = new java.util.ArrayList<>();
        customerThread.get("message").forEach(m -> texts.add(m.get("message").asText()));
        assertThat(texts).containsExactly("yo", "bonjour");

        // Exactly ONE conversation row exists for the (CUSTOMER, RESTAURANT) pair — no matter which
        // façade wrote to it.
        long rows = conversationUnifieeRepo.findByParticipant(ParticipantType.CUSTOMER, customerId).stream()
                .filter(c -> (c.getPartyAType() == ParticipantType.RESTAURANT && c.getPartyAId().equals(restaurantId))
                        || (c.getPartyBType() == ParticipantType.RESTAURANT && c.getPartyBId().equals(restaurantId)))
                .count();
        assertThat(rows).isEqualTo(1);
    }
}
