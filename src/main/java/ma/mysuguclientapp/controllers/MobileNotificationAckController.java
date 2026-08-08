package ma.mysuguclientapp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.MobileNotificationAckDTO;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.MobileNotificationAckService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Réception des événements de cycle de vie envoyés par les apps mobiles. */
@RestController
@RequestMapping("/api/mobile/notification-acks")
@RequiredArgsConstructor
public class MobileNotificationAckController {
    private final UserRepository userRepository;
    private final MobileNotificationAckService ackService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> record(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody MobileNotificationAckDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        var ack = ackService.record(dto, user);
        return ResponseEntity.accepted().body(Map.of(
                "id", ack.getId(),
                "event", ack.getEvent().name(),
                "receivedAt", ack.getCreatedAt() != null ? ack.getCreatedAt() : ack.getOccurredAt()));
    }
}
