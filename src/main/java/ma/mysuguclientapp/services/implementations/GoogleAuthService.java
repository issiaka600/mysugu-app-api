package ma.mysuguclientapp.services.implementations;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.GoogleTokenInfoDTO;
import ma.mysuguclientapp.exceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {
    private static final Duration GOOGLE_TOKEN_INFO_TIMEOUT = Duration.ofSeconds(5);
    private static final String DEFAULT_TOKEN_INFO_FALLBACK_URL = "https://www.googleapis.com/oauth2/v3/tokeninfo";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(GOOGLE_TOKEN_INFO_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Value("${google.auth.client-id:}")
    private String googleClientId;

    @Value("${google.auth.client-ids:}")
    private String googleClientIds;

    @Value("${google.auth.token-info-url:https://oauth2.googleapis.com/tokeninfo}")
    private String tokenInfoUrl;

    @Value("${google.auth.token-info-urls:}")
    private String tokenInfoUrls;

    public GoogleTokenInfoDTO verifyIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BadRequestException("Le token Google est requis");
        }

        List<String> allowedClientIds = resolveAllowedClientIds();
        if (allowedClientIds.isEmpty()) {
            throw new BadRequestException("La configuration Google Auth est incomplete: google.auth.client-id ou google.auth.client-ids manquant");
        }

        Exception lastFailure = null;

        for (String currentTokenInfoUrl : resolveTokenInfoUrls()) {
            try {
                GoogleTokenInfoDTO tokenInfo = fetchTokenInfo(currentTokenInfoUrl, idToken);
                validatePayload(tokenInfo, allowedClientIds);
                return tokenInfo;
            } catch (BadRequestException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                lastFailure = ex;
                break;
            } catch (IOException ex) {
                lastFailure = ex;
                log.warn("Impossible de joindre l'endpoint Google {}: {}", currentTokenInfoUrl, ex.getMessage());
            }
        }

        throw new BadRequestException("Impossible de valider le token Google", lastFailure);
    }

    private GoogleTokenInfoDTO fetchTokenInfo(String currentTokenInfoUrl, String idToken)
            throws IOException, InterruptedException {
        URI uri = UriComponentsBuilder
                .fromUriString(currentTokenInfoUrl)
                .queryParam("id_token", idToken)
                .build()
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(GOOGLE_TOKEN_INFO_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            log.warn("Echec validation token Google via {}: {}", currentTokenInfoUrl, response.body());
            throw new BadRequestException("Le token Google est invalide");
        }
        if (response.statusCode() >= 500) {
            throw new IOException("L'endpoint Google a retourne le statut " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), GoogleTokenInfoDTO.class);
    }

    private void validatePayload(GoogleTokenInfoDTO tokenInfo, List<String> allowedClientIds) {
        if (tokenInfo.getEmail() == null || tokenInfo.getEmail().isBlank()) {
            throw new BadRequestException("Le compte Google ne contient pas d'email exploitable");
        }
        if (!Boolean.parseBoolean(tokenInfo.getEmailVerified())) {
            throw new BadRequestException("L'email Google doit etre verifie");
        }
        if (tokenInfo.getAud() == null || !allowedClientIds.contains(tokenInfo.getAud())) {
            throw new BadRequestException("Le token Google ne correspond pas a un client configure");
        }
    }

    private List<String> resolveAllowedClientIds() {
        LinkedHashSet<String> allowedClientIds = new LinkedHashSet<>();
        addConfiguredValues(allowedClientIds, googleClientId);
        addConfiguredValues(allowedClientIds, googleClientIds);
        return List.copyOf(allowedClientIds);
    }

    private List<String> resolveTokenInfoUrls() {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        addConfiguredValues(urls, tokenInfoUrl);
        addConfiguredValues(urls, tokenInfoUrls);
        urls.add(DEFAULT_TOKEN_INFO_FALLBACK_URL);
        return List.copyOf(urls);
    }

    private void addConfiguredValues(Set<String> target, String rawValues) {
        if (rawValues == null || rawValues.isBlank()) {
            return;
        }

        for (String value : rawValues.split(",")) {
            String normalizedValue = value.trim();
            if (!normalizedValue.isEmpty()) {
                target.add(normalizedValue);
            }
        }
    }
}
