package ma.mysuguclientapp.legacy.deliveryman.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.Notification;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.NotificationRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Notifications du livreur (contrat 6valley §5.13). ⚠ L'app lit la clé `description` (6valley
 * stockait `body`) ; `delivery_man_id` et `order_id` DOIVENT être numériques (fragile).
 */
@RestController
@RequestMapping("/api/v2/delivery-man")
@RequiredArgsConstructor
public class DeliveryManNotificationController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @GetMapping("/notifications")
    public Map<String, Object> notifications(@AuthenticationPrincipal String email,
                                             @RequestParam(defaultValue = "20") int limit,
                                             @RequestParam(defaultValue = "1") int offset) {
        User l = livreur(email);
        var pageReq = PageRequest.of(Math.max(0, offset - 1), Math.max(1, limit));
        var pageResult = notificationRepository.findByDestinataireIdOrderByCreatedAtDesc(l.getId(), pageReq);
        List<Map<String, Object>> list = pageResult.getContent().stream().map(n -> toMap(n, l)).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", (int) pageResult.getTotalElements());
        m.put("limit", String.valueOf(limit));
        m.put("offset", String.valueOf(offset));
        m.put("notifications", list);
        return m;
    }

    private Map<String, Object> toMap(Notification n, User l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("delivery_man_id", l.getId());                              // fragile : numérique
        long orderId = "COMMANDE".equalsIgnoreCase(n.getEntityType()) && n.getEntityId() != null ? n.getEntityId() : 0;
        m.put("order_id", orderId);                                       // fragile : numérique, jamais null
        String desc = n.getMessage() != null ? n.getMessage() : (n.getTitre() != null ? n.getTitre() : "");
        m.put("description", desc);                                       // ⚠ clé `description`
        m.put("created_at", n.getCreatedAt() != null ? n.getCreatedAt().format(TS) : null);
        m.put("updated_at", n.getCreatedAt() != null ? n.getCreatedAt().format(TS) : null);
        return m;
    }

    private User livreur(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Livreur non authentifié"));
        if (u.getRole() != UserRole.LIVREUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux livreurs");
        }
        return u;
    }
}
