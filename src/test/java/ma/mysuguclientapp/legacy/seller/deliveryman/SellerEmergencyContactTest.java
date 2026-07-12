package ma.mysuguclientapp.legacy.seller.deliveryman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.ContactUrgence;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.ContactUrgenceRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3e.6: emergency-contact/* wired to the REAL {@code ContactUrgenceService} (native
 * {@code ContactUrgence}), scoped to {@code restaurant = mine}. This is the ONE part of slice 3e
 * that is NOT a stub. A contact belonging to ANOTHER restaurant -> benign no-op (200, never a
 * cross-restaurant mutation, never 500). Plan 2026-07-10-vendor-3e-deliveryman.md task 3e.6.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerEmergencyContactTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired ContactUrgenceRepository contactRepo;
    @Autowired PasswordEncoder encoder;

    private final String ownerMineEmail = "dm-ec-owner1-" + System.nanoTime() + "@test.mysugu";
    private final String ownerOtherEmail = "dm-ec-owner2-" + System.nanoTime() + "@test.mysugu";
    private Long ownerMineId, ownerOtherId, restoMineId, restoOtherId;
    private Long contactOtherId;

    @BeforeEach
    void seed() {
        ownerMineId = newUser(ownerMineEmail, UserRole.RESTAURANT_OWNER).getId();
        ownerOtherId = newUser(ownerOtherEmail, UserRole.RESTAURANT_OWNER).getId();

        Restaurant restoMine = newResto("Boutique EC Mine", userRepo.findById(ownerMineId).orElseThrow());
        restoMineId = restoMine.getId();
        Restaurant restoOther = newResto("Boutique EC Autre", userRepo.findById(ownerOtherId).orElseThrow());
        restoOtherId = restoOther.getId();

        ContactUrgence other = new ContactUrgence();
        other.setRestaurant(restoOther);
        other.setNom("Contact Autre");
        other.setTelephone("0600000000");
        other.setActif(true);
        contactOtherId = contactRepo.save(other).getId();
    }

    @AfterEach
    void cleanup() {
        // Purge dependent rows before deleting users/restaurants (FK safety).
        contactRepo.findAll().stream()
                .filter(c -> c.getRestaurant() != null
                        && (c.getRestaurant().getId().equals(restoMineId) || c.getRestaurant().getId().equals(restoOtherId)))
                .forEach(contactRepo::delete);
        restoRepo.findById(restoMineId).ifPresent(restoRepo::delete);
        restoRepo.findById(restoOtherId).ifPresent(restoRepo::delete);
        userRepo.findById(ownerMineId).ifPresent(userRepo::delete);
        userRepo.findById(ownerOtherId).ifPresent(userRepo::delete);
    }

    @Test
    void list_is_empty_never_404_when_no_contact_exists() throws Exception {
        String token = token(ownerMineEmail);
        Resp r = get("/api/v3/seller/delivery-man/emergency-contact/list", token);
        assertThat(r.status).isEqualTo(200);

        JsonNode n = M.readTree(r.body);
        assertThat(n.has("contact_list")).isTrue();
        assertThat(n.get("contact_list").isArray()).isTrue();
        assertThat(n.get("contact_list")).isEmpty();
    }

    @Test
    void store_creates_contact_scoped_to_my_restaurant_and_list_returns_it() throws Exception {
        String token = token(ownerMineEmail);
        Resp storeResp = post("/api/v3/seller/delivery-man/emergency-contact/store",
                Map.of("name", "Urgence Cuisine", "phone", "0611111111"), token);
        assertThat(storeResp.status).isEqualTo(200);
        assertThat(M.readTree(storeResp.body).has("message")).isTrue();

        List<ContactUrgence> created = contactRepo.findAll().stream()
                .filter(c -> c.getRestaurant() != null && c.getRestaurant().getId().equals(restoMineId))
                .toList();
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getNom()).isEqualTo("Urgence Cuisine");

        Resp listResp = get("/api/v3/seller/delivery-man/emergency-contact/list", token);
        assertThat(listResp.status).isEqualTo(200);
        JsonNode n = M.readTree(listResp.body);
        assertThat(n.get("contact_list")).hasSize(1);
        assertThat(n.get("contact_list").get(0).get("name").asText()).isEqualTo("Urgence Cuisine");
        // FRAGILE per 3e.0: status must be a non-null JSON boolean (ContactList.fromJson does
        // json['status'] ? 1 : 0 with no null guard).
        assertThat(n.get("contact_list").get(0).get("status").isBoolean()).isTrue();

        contactRepo.deleteAll(created);
    }

    @Test
    void update_on_my_contact_succeeds() throws Exception {
        String token = token(ownerMineEmail);
        ContactUrgence mine = newContact(restoMineId, "Old Name", "0622222222");

        Resp r = post("/api/v3/seller/delivery-man/emergency-contact/update",
                Map.of("_method", "put", "id", mine.getId(), "name", "New Name", "phone", "0622222222"), token);
        assertThat(r.status).isEqualTo(200);

        ContactUrgence updated = contactRepo.findById(mine.getId()).orElseThrow();
        assertThat(updated.getNom()).isEqualTo("New Name");

        contactRepo.delete(updated);
    }

    @Test
    void update_on_another_restaurants_contact_is_benign_no_op_never_500() throws Exception {
        String token = token(ownerMineEmail);
        Resp r = post("/api/v3/seller/delivery-man/emergency-contact/update",
                Map.of("_method", "put", "id", contactOtherId, "name", "Hacked Name", "phone", "0699999999"), token);
        assertThat(r.status).isEqualTo(200);

        ContactUrgence stillOther = contactRepo.findById(contactOtherId).orElseThrow();
        assertThat(stillOther.getNom()).isEqualTo("Contact Autre"); // unchanged
    }

    @Test
    void status_update_on_my_contact_succeeds() throws Exception {
        String token = token(ownerMineEmail);
        ContactUrgence mine = newContact(restoMineId, "Status Test", "0633333333");

        Resp r = post("/api/v3/seller/delivery-man/emergency-contact/status-update",
                Map.of("_method", "put", "id", mine.getId(), "status", 0), token);
        assertThat(r.status).isEqualTo(200);

        ContactUrgence updated = contactRepo.findById(mine.getId()).orElseThrow();
        assertThat(updated.getActif()).isFalse();

        contactRepo.delete(updated);
    }

    @Test
    void status_update_on_another_restaurants_contact_is_benign_no_op_never_500() throws Exception {
        String token = token(ownerMineEmail);
        Resp r = post("/api/v3/seller/delivery-man/emergency-contact/status-update",
                Map.of("_method", "put", "id", contactOtherId, "status", 0), token);
        assertThat(r.status).isEqualTo(200);

        ContactUrgence stillOther = contactRepo.findById(contactOtherId).orElseThrow();
        assertThat(stillOther.getActif()).isTrue(); // unchanged
    }

    @Test
    void delete_on_my_contact_succeeds() throws Exception {
        String token = token(ownerMineEmail);
        ContactUrgence mine = newContact(restoMineId, "Delete Me", "0644444444");

        Resp r = post("/api/v3/seller/delivery-man/emergency-contact/delete",
                Map.of("_method", "delete", "id", mine.getId()), token);
        assertThat(r.status).isEqualTo(200);

        assertThat(contactRepo.findById(mine.getId())).isEmpty();
    }

    @Test
    void delete_on_another_restaurants_contact_is_benign_no_op_never_500() throws Exception {
        String token = token(ownerMineEmail);
        Resp r = post("/api/v3/seller/delivery-man/emergency-contact/delete",
                Map.of("_method", "delete", "id", contactOtherId), token);
        assertThat(r.status).isEqualTo(200);

        // Not deleted — the "other" restaurant's contact must survive a foreign vendor's delete call.
        assertThat(contactRepo.findById(contactOtherId)).isPresent();
    }

    private ContactUrgence newContact(Long restaurantId, String nom, String tel) {
        ContactUrgence c = new ContactUrgence();
        c.setRestaurant(restoRepo.findById(restaurantId).orElseThrow());
        c.setNom(nom);
        c.setTelephone(tel);
        c.setActif(true);
        return contactRepo.save(c);
    }

    private User newUser(String email, UserRole role) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("N"); u.setPrenom("P");
        u.setRole(role);
        u.setIsActive(true);
        return userRepo.save(u);
    }

    private Restaurant newResto(String nom, User owner) {
        Restaurant r = new Restaurant();
        r.setNom(nom);
        r.setDescription("desc");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(owner);
        r.setIsActive(true);
        r.setLocalisation(new Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        return restoRepo.save(r);
    }

    private String token(String email) throws Exception {
        Resp r = post("/api/v3/seller/auth/login", Map.of("email", email, "password", "demo1234"), null);
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
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
