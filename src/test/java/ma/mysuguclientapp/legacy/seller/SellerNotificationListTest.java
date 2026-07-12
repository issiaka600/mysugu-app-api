package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.Notification;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.TypeNotification;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.NotificationRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat GET /api/v3/seller/notification?limit=&offset= : {@code Page<NotificationDTO>} du
 * vendeur authentifié (via {@code NotificationService.getMesNotifications}, token forward) ->
 * enveloppe 6valley. Clé de liste réelle {@code notification} (singulier) et champ
 * {@code notification_seen_status} confirmés via Tiktak-vendor-app-moso
 * (NotificationItemModel.fromJson lit {@code json['notification']}, PAS {@code notifications} ;
 * NotificationItem lit {@code notification_seen_status}, PAS {@code is_read} — correction 3j.0
 * par rapport à la conception initiale de la spec). {@code is_read} conservé en bonus. Plan 3j.4.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerNotificationListTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired NotificationRepository notificationRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "notif-list-" + System.nanoTime() + "@test.mysugu";
    private final String otherOwnerEmail = "notif-list-other-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, otherOwnerId;
    private final List<Long> notifIds = new ArrayList<>();
    private Long readNotifId;

    @BeforeEach
    void seed() {
        ownerId = newOwner(ownerEmail);
        otherOwnerId = newOwner(otherOwnerEmail);

        User owner = userRepo.findById(ownerId).orElseThrow();
        notifIds.add(newNotif(owner, "Titre 1", false).getId());
        Notification n2 = newNotif(owner, "Titre 2", true);
        readNotifId = n2.getId();
        notifIds.add(readNotifId);
        notifIds.add(newNotif(owner, "Titre 3", false).getId());

        // bruit d'un AUTRE vendeur : jamais visible pour ownerEmail (isolation)
        notifIds.add(newNotif(userRepo.findById(otherOwnerId).orElseThrow(), "Titre Autre", false).getId());
    }

    @AfterEach
    void cleanup() {
        notifIds.forEach(id -> notificationRepo.findById(id).ifPresent(notificationRepo::delete));
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
        userRepo.findById(otherOwnerId).ifPresent(userRepo::delete);
    }

    @Test
    void list_returns_envelope_with_owner_notifications_only() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/notification?limit=2&offset=0", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("total_size").asInt()).isEqualTo(3);
        assertThat(n.has("limit")).isTrue();
        assertThat(n.has("offset")).isTrue();
        assertThat(n.has("notification")).isTrue();
        assertThat(n.get("notification").size()).isEqualTo(2);

        JsonNode item = n.get("notification").get(0);
        assertThat(item.has("id")).isTrue();
        assertThat(item.has("title")).isTrue();
        assertThat(item.has("description")).isTrue();
        assertThat(item.has("notification_seen_status")).isTrue();
        assertThat(item.has("created_at")).isTrue();
    }

    @Test
    void list_second_page_returns_remaining_item() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/notification?limit=2&offset=2", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.get("total_size").asInt()).isEqualTo(3);
        assertThat(n.get("notification").size()).isEqualTo(1);
    }

    @Test
    void list_marks_read_item_notification_seen_status_one() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/notification?limit=10&offset=0", token);
        JsonNode n = M.readTree(r.body);

        boolean foundReadOne = false;
        for (JsonNode item : n.get("notification")) {
            if (item.get("id").asLong() == readNotifId) {
                assertThat(item.get("notification_seen_status").asInt()).isEqualTo(1);
                foundReadOne = true;
            } else {
                assertThat(item.get("notification_seen_status").asInt()).isEqualTo(0);
            }
        }
        assertThat(foundReadOne).isTrue();
    }

    @Test
    void other_owner_never_sees_this_owners_notifications() throws Exception {
        String token = token(otherOwnerEmail);
        Resp r = get("/api/v3/seller/notification?limit=10&offset=0", token);
        JsonNode n = M.readTree(r.body);
        assertThat(n.get("total_size").asInt()).isEqualTo(1);
    }

    private Long newOwner(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("N"); u.setPrenom("P");
        u.setRole(UserRole.RESTAURANT_OWNER);
        u.setIsActive(true);
        return userRepo.save(u).getId();
    }

    private Notification newNotif(User destinataire, String titre, boolean lue) {
        Notification n = Notification.builder()
                .destinataire(destinataire)
                .titre(titre)
                .message("desc " + titre)
                .type(TypeNotification.SYSTEME)
                .lue(lue)
                .lueAt(lue ? LocalDateTime.now() : null)
                .build();
        return notificationRepo.save(n);
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
