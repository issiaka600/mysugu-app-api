package ma.mysuguclientapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import ma.mysuguclientapp.dtos.LoginResponseDTO;
import ma.mysuguclientapp.repositories.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GoogleAuthHttpFlowE2ETest {
    private static final String PRIMARY_CLIENT_ID = "primary-web-client.apps.googleusercontent.com";
    private static final String SECONDARY_CLIENT_ID = "secondary-web-client.apps.googleusercontent.com";
    private static final String TEST_EMAIL = "google.auth.e2e@mysugu.test";
    private static final String TEST_ID_TOKEN = "fake-google-id-token";

    private static final AtomicInteger TOKEN_INFO_STATUS = new AtomicInteger(200);
    private static final AtomicInteger TOKEN_INFO_CALL_COUNT = new AtomicInteger();
    private static final AtomicReference<String> TOKEN_INFO_BODY = new AtomicReference<>();
    private static final AtomicReference<String> LAST_QUERY = new AtomicReference<>();
    private static final HttpServer TOKEN_INFO_SERVER = startTokenInfoServer();

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("google.auth.client-id", () -> PRIMARY_CLIENT_ID);
        registry.add("google.auth.client-ids", () -> SECONDARY_CLIENT_ID);
        registry.add("google.auth.token-info-url", GoogleAuthHttpFlowE2ETest::tokenInfoUrl);
        registry.add("google.auth.token-info-urls", () -> "http://127.0.0.1:1/tokeninfo," + tokenInfoUrl());
        registry.add("firebase.enabled", () -> "false");
    }

    @BeforeEach
    void setUp() {
        TOKEN_INFO_STATUS.set(200);
        TOKEN_INFO_CALL_COUNT.set(0);
        LAST_QUERY.set(null);
        TOKEN_INFO_BODY.set(validTokenInfoBody(TEST_EMAIL, SECONDARY_CLIENT_ID));
        userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
    }

    @AfterEach
    void cleanUp() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
    }

    @AfterAll
    static void stopTokenInfoServer() {
        TOKEN_INFO_SERVER.stop(0);
    }

    @Test
    void googleLogin_usesFallbackEndpoint_andAcceptsSecondaryConfiguredClientId() throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/google"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "idToken": "%s"
                        }
                        """.formatted(TEST_ID_TOKEN)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        LoginResponseDTO loginResponse = objectMapper.readValue(response.body(), LoginResponseDTO.class);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.getToken()).isNotBlank();
        assertThat(loginResponse.getUser()).isNotNull();
        assertThat(loginResponse.getUser().getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(loginResponse.getUser().getRole()).isEqualTo("CLIENT");
        assertThat(TOKEN_INFO_CALL_COUNT.get()).isEqualTo(1);
        assertThat(LAST_QUERY.get()).contains("id_token=" + TEST_ID_TOKEN);
        assertThat(userRepository.findByEmail(TEST_EMAIL)).isPresent();
    }

    private static HttpServer startTokenInfoServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/tokeninfo", exchange -> {
                TOKEN_INFO_CALL_COUNT.incrementAndGet();
                LAST_QUERY.set(exchange.getRequestURI().getRawQuery());

                byte[] responseBody = TOKEN_INFO_BODY.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(TOKEN_INFO_STATUS.get(), responseBody.length);

                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBody);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException("Impossible de demarrer le faux endpoint Google", ex);
        }
    }

    private static String tokenInfoUrl() {
        return "http://127.0.0.1:" + TOKEN_INFO_SERVER.getAddress().getPort() + "/tokeninfo";
    }

    private static String validTokenInfoBody(String email, String aud) {
        return """
                {
                  "aud": "%s",
                  "email": "%s",
                  "email_verified": "true",
                  "exp": "1893456000",
                  "sub": "google-sub-123",
                  "name": "Google E2E",
                  "given_name": "Google",
                  "family_name": "E2E",
                  "picture": "https://example.com/avatar.png"
                }
                """.formatted(aud, email);
    }
}
