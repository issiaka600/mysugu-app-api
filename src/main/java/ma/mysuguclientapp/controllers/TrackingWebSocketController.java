package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.tracking.GpsLocationDTO;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@Slf4j
public class TrackingWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Livreur sends GPS position update.
     * Clients subscribe to /topic/tracking/{commandeId}
     */
    @MessageMapping("/tracking.update")
    public void updateLocation(@Payload GpsLocationDTO location) {
        location.setTimestamp(LocalDateTime.now());
        log.debug("GPS update for commande {}: {},{}", location.getCommandeId(),
                location.getLatitude(), location.getLongitude());

        // Broadcast to clients watching this commande
        messagingTemplate.convertAndSend(
                "/topic/tracking/" + location.getCommandeId(),
                location
        );
    }
}
