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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat GET /api/v3/seller/notification/view?id= : marque {@code lue=true} (native
 * {@code marquerCommeLue}) et renvoie une forme bénigne (l'app ne lit que le statusCode —
 * NotificationController.seenNotification, 3j.0). GARDE CRITIQUE de cette tranche : une
 * notification appartenant à un AUTRE vendeur -> 404, jamais marquée (le shim revérifie
 * l'appartenance AVANT tout appel au service natif — celui-ci lèverait un 401, pas le 404 attendu
 * ici). id absent/introuvable -> 404 également, jamais 500. Plan 3j.5.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerNotificationViewTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired NotificationRepository notificationRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerEmail = "notif-view-" + System.nanoTime() + "@test.mysugu";
    private final String otherOwnerEmail = "notif-view-other-" + System.nanoTime() + "@test.mysugu";
    private Long ownerId, otherOwnerId;
    private final List<Long> notifIds = new ArrayList<>();
    private Long ownNotifId, otherOwnersNotifId;

    @BeforeEach
    void seed() {
        ownerId = newOwner(ownerEmail);
        otherOwnerId = newOwner(otherOwnerEmail);

        ownNotifId = newNotif(userRepo.findById(ownerId).orElseThrow(), "Mine").getId();
        otherOwnersNotifId = newNotif(userRepo.findById(otherOwnerId).orElseThrow(), "Not mine").getId();
        notifIds.add(ownNotifId);
        notifIds.add(otherOwnersNotifId);
    }

    @AfterEach
    void cleanup() {
        notifIds.forEach(id -> notificationRepo.findById(id).ifPresent(notificationRepo::delete));
        userRepo.findById(ownerId).ifPresent(userRepo::delete);
        userRepo.findById(otherOwnerId).ifPresent(userRepo::delete);
    }

    @Test
    void view_marks_owners_own_notification_seen() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/notification/view?id=" + ownNotifId, token);
        assertThat(r.status).isEqualTo(200);
        JsonNode n = M.readTree(r.body);
        assertThat(n.has("message")).isTrue();

        Notification reloaded = notificationRepo.findById(ownNotifId).orElseThrow();
        assertThat(reloaded.getLue()).isTrue();
        assertThat(reloaded.getLueAt()).isNotNull();
    }

    @Test
    void view_on_another_owners_notification_returns_404_and_never_marks_it() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/notification/view?id=" + otherOwnersNotifId, token);
        assertThat(r.status).isEqualTo(404);

        Notification stillUnread = notificationRepo.findById(otherOwnersNotifId).orElseThrow();
        assertThat(stillUnread.getLue()).isFalse();
    }

    @Test
    void view_on_absent_id_is_benign_404_never_500() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/notification/view?id=9999999", token);
        assertThat(r.status).isEqualTo(404);
    }

    @Test
    void view_without_id_param_is_benign_404_never_500() throws Exception {
        String token = token(ownerEmail);
        Resp r = get("/api/v3/seller/notification/view", token);
        assertThat(r.status).isEqualTo(404);
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

    private Notification newNotif(User destinataire, String titre) {
        Notification n = Notification.builder()
                .destinataire(destinataire)
                .titre(titre)
                .message("desc " + titre)
                .type(TypeNotification.SYSTEME)
                .lue(false)
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
