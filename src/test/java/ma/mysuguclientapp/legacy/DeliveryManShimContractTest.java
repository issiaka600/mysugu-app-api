package ma.mysuguclientapp.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.enumerations.*;
import ma.mysuguclientapp.repositories.*;
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
import static org.mockito.Mockito.when;

/**
 * Tests de contrat du shim livreur (/api/v2/delivery-man/*). Rejoue les flux critiques de bout en
 * bout (vrai HTTP via le HttpClient du JDK) contre le Postgres de test du projet
 * (voir src/test/resources/application.properties — conteneur `mysugu-test-db` sur :5433),
 * en mockant les dépendances externes (MinIO, FCM, email). Vérifie les formes 6valley + le
 * modèle argent complet, retrait partiel inclus.
 * Prérequis pour exécuter : un Postgres `mysugu_test`/test/test accessible (comme les autres tests
 * d'intégration du projet). Voir docs/LIVREUR_BACKEND_LOGIC.md + legacy-contracts/CONTRACT-REFERENCE.md.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class DeliveryManShimContractTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restaurantRepo;
    @Autowired CommandeRepository commandeRepo;
    @Autowired OtpResetLivreurRepository otpRepo;
    @Autowired PreuveLivraisonRepository preuveRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    private Long livreurId;
    private Long orderId;

    @BeforeEach
    void seed() throws Exception {
        when(minioService.uploadFile(any(), anyString())).thenReturn("https://test/proof.png");
        // Table rase entre chaque test : TRUNCATE CASCADE de toutes les tables publiques
        // (gère les FK automatiquement, réinitialise les identités).
        jdbc.execute("DO $$ DECLARE r RECORD; BEGIN " +
                "FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname='public') LOOP " +
                "EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' RESTART IDENTITY CASCADE'; " +
                "END LOOP; END $$;");

        User livreur = newUser("livreur.test@mysugu.local", "Test", "Livreur", UserRole.LIVREUR);
        livreur.setTelephone("+212600000001");
        livreur.setCountryCode("+212");
        livreurId = userRepo.save(livreur).getId();
        User admin = userRepo.save(newUser("admin.test@mysugu.local", "Admin", "Test", UserRole.ADMIN));
        User client = userRepo.save(newUser("client.test@mysugu.local", "Client", "Test", UserRole.CLIENT));

        Restaurant r = new Restaurant();
        r.setNom("Resto Test");
        r.setOwner(admin);
        r = restaurantRepo.save(r);

        Commande c = new Commande();
        c.setNumeroCommande("SHIM-TEST-1");
        c.setClient(client);
        c.setRestaurant(r);
        c.setStatut(StatutCommande.EN_PREPARATION);      // non assignée => revendiquable en FCFS
        c.setModeReception(ModeReceptionCommande.LIVRAISON);
        c.setMethodePaiement(MethodePaiement.ESPECES);   // COD
        c.setStatutPaiement(StatutPaiement.EN_ATTENTE);
        c.setMontantTotal(new BigDecimal("130.00"));
        c.setMontantFinal(new BigDecimal("130.00"));
        c.setFraisLivraison(new BigDecimal("15.00"));
        orderId = commandeRepo.save(c).getId();
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

    private Resp doPost(String path, Object body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body)));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp doGet(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(path))).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private String loginLivreur(String pwd) throws Exception {
        JsonNode n = M.readTree(doPost("/api/v2/delivery-man/auth/login",
                Map.of("country_code", "+212", "phone", "+212600000001", "password", pwd), null).body());
        return n.hasNonNull("token") ? n.get("token").asText() : null;
    }

    private String loginNative(String email, String pwd) throws Exception {
        JsonNode n = M.readTree(doPost("/auth/login", Map.of("email", email, "password", pwd), null).body());
        return n.hasNonNull("token") ? n.get("token").asText() : null;
    }

    private JsonNode info(String token) throws Exception {
        return M.readTree(doGet("/api/v2/delivery-man/info", token).body());
    }

    private BigDecimal num(JsonNode n, String key) {
        return new BigDecimal(n.get(key).asText());
    }

    // ---------- tests ----------

    @Test
    void login_realAppSplitPayload_succeeds() throws Exception {
        // Reproduit le payload RÉEL de l'app Tiktak : indicatif sans '+' ("212") et numéro LOCAL
        // ("600000001", tel que saisi, sans indicatif), alors que User.telephone est stocké en
        // E.164 ("+212600000001"). L'ancien matching exact renvoyait 401 ; la normalisation corrige.
        Resp r = doPost("/api/v2/delivery-man/auth/login",
                Map.of("country_code", "212", "phone", "600000001", "password", "demo1234"), null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(M.readTree(r.body()).hasNonNull("token")).isTrue();
    }

    @Test
    void config_hasLanguageAndUnitAsArrays() throws Exception {
        JsonNode c = M.readTree(doGet("/api/v1/config", null).body());
        assertThat(c.get("language").isArray()).isTrue();
        assertThat(c.get("unit").isArray()).isTrue();
        assertThat(c.get("base_urls")).isNotNull();
        // shipping_method = sellerwise_shipping : requis pour que l'app vendeur affiche
        // le bouton d'assignation de livreur (delivery_man_assign_widget.dart).
        assertThat(c.get("shipping_method").asText()).isEqualTo("sellerwise_shipping");
    }

    @Test
    void login_and_info_expose_contract_fields() throws Exception {
        String t = loginLivreur("demo1234");
        assertThat(t).isNotBlank();
        JsonNode i = info(t);
        assertThat(i.get("is_online").isInt()).isTrue();          // clé fragile
        assertThat(i.get("identity_image").asText()).isEqualTo("[]"); // clé fragile (chaîne JSON)
        for (String k : new String[]{"current_balance", "cash_in_hand", "withdrawable_balance",
                "pending_withdraw", "total_withdraw", "total_deposit", "total_earn"}) {
            assertThat(i.has(k)).as(k).isTrue();
        }
    }

    @Test
    void accept_then_deliver_writes_gains_and_caisse() throws Exception {
        String t = loginLivreur("demo1234");
        assertThat(doPost("/api/v2/delivery-man/" + orderId + "/accept", Map.of(), t).status()).isEqualTo(200);
        assertThat(doPost("/api/v2/delivery-man/update-order-status",
                Map.of("order_id", orderId, "status", "delivered"), t).status()).isEqualTo(200);
        JsonNode i = info(t);
        assertThat(num(i, "current_balance")).isEqualByComparingTo("12.75"); // 0.85 * 15
        assertThat(num(i, "cash_in_hand")).isEqualByComparingTo("130.00");   // montantFinal
        assertThat(num(i, "withdrawable_balance")).isEqualByComparingTo("0"); // cash détenu bloque
    }

    @Test
    void full_money_lifecycle_partial_withdrawal() throws Exception {
        String lt = loginLivreur("demo1234");
        String at = loginNative("admin.test@mysugu.local", "demo1234");
        doPost("/api/v2/delivery-man/" + orderId + "/accept", Map.of(), lt);
        doPost("/api/v2/delivery-man/update-order-status", Map.of("order_id", orderId, "status", "delivered"), lt);
        assertThat(doPost("/api/caisse/reconcilier", Map.of("livreurId", livreurId), at).status()).isEqualTo(200);
        JsonNode afterRecon = info(lt);
        assertThat(num(afterRecon, "cash_in_hand")).isEqualByComparingTo("0");
        assertThat(num(afterRecon, "withdrawable_balance")).isEqualByComparingTo("12.75");
        assertThat(doPost("/api/v2/delivery-man/withdraw-request", Map.of("amount", "10", "note", "x"), lt).status())
                .isEqualTo(200);
        String wid = M.readTree(doGet("/api/admin/livreurs/retraits/en-attente", at).body()).get(0).get("id").asText();
        assertThat(doPost("/api/admin/livreurs/retraits/" + wid + "/approuver", Map.of(), at).status()).isEqualTo(200);
        JsonNode fin = info(lt);
        assertThat(num(fin, "current_balance")).isEqualByComparingTo("2.75"); // 12.75 - 10 (retrait partiel)
        assertThat(num(fin, "total_withdraw")).isEqualByComparingTo("10");
    }

    @Test
    void otp_reset_flow_changes_password() throws Exception {
        assertThat(doPost("/api/v2/delivery-man/auth/forgot-password",
                Map.of("country_code", "+212", "phone", "+212600000001"), null).status()).isEqualTo(200);
        String code = otpRepo.findFirstByTelephoneAndUsedAtIsNullOrderByCreatedAtDesc("+212600000001")
                .orElseThrow().getCode();
        assertThat(doPost("/api/v2/delivery-man/auth/verify-otp",
                Map.of("otp", code, "phone", "+212600000001"), null).status()).isEqualTo(200);
        assertThat(doPost("/api/v2/delivery-man/auth/reset-password",
                Map.of("phone", "+212600000001", "password", "NewPass2026!", "confirm_password", "NewPass2026!"),
                null).status()).isEqualTo(200);
        assertThat(loginLivreur("NewPass2026!")).isNotBlank();
    }

    @Test
    void delivering_unknown_order_returns_404_not_500() throws Exception {
        String t = loginLivreur("demo1234");
        // Le fix GlobalExceptionHandler(ResponseStatusException) garantit 404, pas 500.
        assertThat(doPost("/api/v2/delivery-man/update-order-status",
                Map.of("order_id", 999999, "status", "delivered"), t).status()).isEqualTo(404);
    }

    @Test
    void proof_upload_persists_row() throws Exception {
        String t = loginLivreur("demo1234");
        doPost("/api/v2/delivery-man/" + orderId + "/accept", Map.of(), t);
        String boundary = "----shimtestboundary";
        String CRLF = "\r\n";
        String body = "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"order_id\"" + CRLF + CRLF
                + orderId + CRLF
                + "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"image\"; filename=\"proof.png\"" + CRLF
                + "Content-Type: image/png" + CRLF + CRLF
                + "imgbytes" + CRLF
                + "--" + boundary + "--" + CRLF;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url("/api/v2/delivery-man/order-delivery-verification")))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + t)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(preuveRepo.findByCommandeIdOrderByCreatedAtAsc(orderId)).isNotEmpty();
    }
}
