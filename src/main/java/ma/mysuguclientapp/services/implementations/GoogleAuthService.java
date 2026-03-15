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

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${google.auth.client-id:}")
    private String googleClientId;

    @Value("${google.auth.token-info-url:https://oauth2.googleapis.com/tokeninfo}")
    private String tokenInfoUrl;

    public GoogleTokenInfoDTO verifyIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BadRequestException("Le token Google est requis");
        }
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new BadRequestException("La configuration Google Auth est incomplète: google.auth.client-id manquant");
        }

        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(tokenInfoUrl)
                    .queryParam("id_token", idToken)
                    .build()
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Echec validation token Google: {}", response.body());
                throw new BadRequestException("Le token Google est invalide");
            }

            GoogleTokenInfoDTO tokenInfo = objectMapper.readValue(response.body(), GoogleTokenInfoDTO.class);
            validatePayload(tokenInfo);
            return tokenInfo;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Impossible de valider le token Google", e);
        } catch (IOException e) {
            throw new BadRequestException("Impossible de valider le token Google", e);
        }
    }

    private void validatePayload(GoogleTokenInfoDTO tokenInfo) {
        if (tokenInfo.getEmail() == null || tokenInfo.getEmail().isBlank()) {
            throw new BadRequestException("Le compte Google ne contient pas d'email exploitable");
        }
        if (!Boolean.parseBoolean(tokenInfo.getEmailVerified())) {
            throw new BadRequestException("L'email Google doit être vérifié");
        }
        if (!googleClientId.equals(tokenInfo.getAud())) {
            throw new BadRequestException("Le token Google ne correspond pas au client configuré");
        }
    }
}
