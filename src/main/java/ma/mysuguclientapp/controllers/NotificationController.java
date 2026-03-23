package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.NotificationDTO;
import ma.mysuguclientapp.services.interfaces.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> getMesNotifications(
            @RequestHeader("Authorization") String token,
            Pageable pageable) {
        return ResponseEntity.ok(notificationService.getMesNotifications(token, pageable));
    }

    @GetMapping("/non-lues")
    public ResponseEntity<List<NotificationDTO>> getMesNotificationsNonLues(
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(notificationService.getMesNotificationsNonLues(token));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getNombreNonLues(
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(Map.of("nonLues", notificationService.getNombreNonLues(token)));
    }

    @PatchMapping("/{id}/lire")
    public ResponseEntity<NotificationDTO> marquerCommeLue(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.marquerCommeLue(token, id));
    }

    @PostMapping("/lire-toutes")
    public ResponseEntity<Map<String, String>> marquerToutesCommeLues(
            @RequestHeader("Authorization") String token) {
        notificationService.marquerToutesCommeLues(token);
        return ResponseEntity.ok(Map.of("message", "Toutes les notifications ont été marquées comme lues."));
    }
}
