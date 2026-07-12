package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.EmailService;
import ma.mysuguclientapp.services.implementations.MinioService;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Contrat images (mono) du shim vendeur : POST products/upload-images, POST
 * products/delete-image, GET products/get-product-images/{id}. Plat est mono-image
 * (imageUrl) — pas de galerie (GAP, umbrella §7.2). Ownership TOUJOURS vérifiée. Voir plan
 * 3c.5.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerProductImagesTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PlatRepository platRepo;
    @Autowired PasswordEncoder encoder;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private final String owner1Email = "prod-img1-" + System.nanoTime() + "@test.mysugu";
    private final String owner2Email = "prod-img2-" + System.nanoTime() + "@test.mysugu";
    private Long owner1Id, owner2Id, resto1Id, resto2Id, plat1Id, plat2Id;

    @BeforeEach
    void seed() throws Exception {
        owner1Id = newOwner(owner1Email);
        owner2Id = newOwner(owner2Email);
        resto1Id = newResto("Boutique Un", owner1Id);
        resto2Id = newResto("Boutique Deux", owner2Id);
        plat1Id = newPlat("Tajine", resto1Id);
        plat2Id = newPlat("Pizza", resto2Id);

        Mockito.when(minioService.uploadFile(any(), anyString())).thenReturn("plats/uploaded.jpg");
        Mockito.when(minioService.buildPublicFileUrl(anyString()))
                .thenAnswer(inv -> "http://files.mysugu/" + inv.getArgument(0));
    }

    @AfterEach
    void cleanup() {
        platRepo.findById(plat1Id).ifPresent(platRepo::delete);
        platRepo.findById(plat2Id).ifPresent(platRepo::delete);
        restoRepo.findById(resto1Id).ifPresent(restoRepo::delete);
        restoRepo.findById(resto2Id).ifPresent(restoRepo::delete);
        userRepo.findById(owner1Id).ifPresent(userRepo::delete);
        userRepo.findById(owner2Id).ifPresent(userRepo::delete);
    }

    @Test
    void upload_then_get_then_delete_image_roundtrip() throws Exception {
        String t1 = token(owner1Email);

        Resp getBefore = get("/api/v3/seller/products/get-product-images/" + plat1Id, t1);
        assertThat(getBefore.status).isEqualTo(200);
        assertThat(M.readTree(getBefore.body)).isEmpty();

        Resp upload = postMultipartFile("/api/v3/seller/products/upload-images", t1, plat1Id, "image.jpg");
        assertThat(upload.status).isEqualTo(200);
        JsonNode uploadJson = M.readTree(upload.body);
        assertThat(uploadJson.get("image")).hasSize(1);

        Resp getAfter = get("/api/v3/seller/products/get-product-images/" + plat1Id, t1);
        JsonNode images = M.readTree(getAfter.body);
        assertThat(images).hasSize(1);

        Resp del = postJson("/api/v3/seller/products/delete-image", t1, Map.of("id", plat1Id));
        assertThat(del.status).isEqualTo(200);

        Resp getFinal = get("/api/v3/seller/products/get-product-images/" + plat1Id, t1);
        assertThat(M.readTree(getFinal.body)).isEmpty();
    }

    @Test
    void upload_image_of_other_restaurant_product_returns_404() throws Exception {
        String t1 = token(owner1Email);
        Resp r = postMultipartFile("/api/v3/seller/products/upload-images", t1, plat2Id, "image.jpg");
        assertThat(r.status).isEqualTo(404);
    }

    @Test
    void delete_image_of_other_restaurant_product_returns_404() throws Exception {
        String t1 = token(owner1Email);
        Resp r = postJson("/api/v3/seller/products/delete-image", t1, Map.of("id", plat2Id));
        assertThat(r.status).isEqualTo(404);
    }

    @Test
    void get_images_of_other_restaurant_product_returns_404() throws Exception {
        String t1 = token(owner1Email);
        Resp r = get("/api/v3/seller/products/get-product-images/" + plat2Id, t1);
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

    private Long newResto(String nom, Long ownerId) {
        Restaurant r = new Restaurant();
        r.setNom(nom);
        r.setDescription("desc");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(userRepo.findById(ownerId).orElseThrow());
        r.setIsActive(true);
        r.setLocalisation(new Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        return restoRepo.save(r).getId();
    }

    private Long newPlat(String nom, Long restoId) {
        Plat p = new Plat();
        p.setNom(nom);
        p.setDescription("desc " + nom);
        p.setPrix(new BigDecimal("30.00"));
        p.setRestaurant(restoRepo.findById(restoId).orElseThrow());
        p.setIsAvailable(true);
        return platRepo.save(p).getId();
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

    private Resp get(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp postMultipartFile(String path, String token, Long id, String fileName) throws Exception {
        String boundary = "----productimageboundary";
        String CRLF = "\r\n";
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append(CRLF)
          .append("Content-Disposition: form-data; name=\"id\"").append(CRLF)
          .append(CRLF).append(id).append(CRLF);
        sb.append("--").append(boundary).append(CRLF)
          .append("Content-Disposition: form-data; name=\"image\"; filename=\"").append(fileName).append("\"").append(CRLF)
          .append("Content-Type: image/jpeg").append(CRLF)
          .append(CRLF).append("fake-image-bytes").append(CRLF);
        sb.append("--").append(boundary).append("--").append(CRLF);

        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
