package ma.mysuguclientapp.legacy.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.services.implementations.EmailService;
import ma.mysuguclientapp.services.implementations.MinioService;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des endpoints boot partagés (stubs 6valley) : chacun renvoie 200 avec une forme bénigne
 * non-null, JAMAIS 500/404. /api/v1/config (déjà implémenté par ConfigLegacyController) est
 * réaffirmé ici (tableau language non-null).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerSharedTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @MockitoBean MinioService minioService;
    @MockitoBean FcmService fcmService;
    @MockitoBean EmailService emailService;

    @Test
    void attributes_returns_empty_array() throws Exception {
        Resp r = get("/api/v1/attributes");
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).isArray()).isTrue();
    }

    @Test
    void categories_childes_return_empty_arrays() throws Exception {
        for (String p : new String[]{"/api/v1/categories/childes/", "/api/v1/categories/childes/childes/"}) {
            Resp r = get(p);
            assertThat(r.status).as(p).isEqualTo(200);
            assertThat(M.readTree(r.body).isArray()).as(p).isTrue();
        }
    }

    @Test
    void mapapi_returns_zero_results_shape() throws Exception {
        for (String p : new String[]{"/api/v1/mapapi/geocode-api",
                "/api/v1/mapapi/place-api-autocomplete", "/api/v1/mapapi/place-api-details"}) {
            Resp r = get(p);
            assertThat(r.status).as(p).isEqualTo(200);
            JsonNode n = M.readTree(r.body);
            assertThat(n.get("status").asText()).as(p).isEqualTo("ZERO_RESULTS");
            assertThat(n.get("results").isArray()).as(p).isTrue();
        }
    }

    @Test
    void config_language_is_non_null_array() throws Exception {
        Resp r = get("/api/v1/config");
        assertThat(r.status).isEqualTo(200);
        assertThat(M.readTree(r.body).get("language").isArray()).isTrue();
    }

    private Resp get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {}
}
