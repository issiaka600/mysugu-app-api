package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.tracking.GpsLocationDTO;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.tracking.TrackingLocationPersistenceService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@Slf4j
public class TrackingWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final TrackingLocationPersistenceService trackingLocationPersistenceService;
    private final UserRepository userRepository;

    /**
     * Livreur sends GPS position update.
     * Clients subscribe to /topic/tracking/{commandeId}
     */
    @MessageMapping("/tracking.update")
    public void updateLocation(@Payload GpsLocationDTO location, Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("JWT WebSocket requis");
        }
        User livreur = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new AccessDeniedException("Livreur WebSocket introuvable"));
        if (livreur.getRole() != UserRole.LIVREUR) {
            throw new AccessDeniedException("Seul un livreur peut envoyer une position GPS");
        }
        location.setLivreurId(livreur.getId());
        location.setTimestamp(LocalDateTime.now());
        location = trackingLocationPersistenceService.save(location);
        log.debug("GPS update for commande {}: {},{}", location.getCommandeId(),
                location.getLatitude(), location.getLongitude());

        // Broadcast to clients watching this commande
        messagingTemplate.convertAndSend(
                "/topic/tracking/" + location.getCommandeId(),
                location
        );
    }
}
