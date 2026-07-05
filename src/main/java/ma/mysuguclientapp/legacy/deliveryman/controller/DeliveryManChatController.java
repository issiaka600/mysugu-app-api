package ma.mysuguclientapp.legacy.deliveryman.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.MessageLivreur;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.MessageLivreurRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.MinioService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Chat livreur (contrat 6valley §5.14–§5.17). Feature reconstruite (absente de MySugu).
 * ⚠ `sent_by_customer/seller/admin` DOIVENT être des booléens JSON (fragile côté app).
 * Le counterpart (réponse côté client/vendeur) reste à câbler dans l'app client (techspec §9).
 */
@RestController
@RequestMapping("/api/v2/delivery-man/messages")
@RequiredArgsConstructor
@Slf4j
public class DeliveryManChatController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final MessageLivreurRepository messageRepository;
    private final MinioService minioService;

    /** Liste des conversations d'un type : dernier message par interlocuteur. */
    @GetMapping("/list/{type}")
    public Map<String, Object> list(@AuthenticationPrincipal String email, @PathVariable String type,
                                    @RequestParam(defaultValue = "10") int limit,
                                    @RequestParam(defaultValue = "1") int offset) {
        User l = livreur(email);
        List<MessageLivreur> all = messageRepository.findByLivreurIdAndInterlocuteurTypeOrderByCreatedAtDesc(l.getId(), type);
        // Dernier message par interlocuteur (all est trié récent d'abord).
        Map<Long, MessageLivreur> latest = new LinkedHashMap<>();
        for (MessageLivreur m : all) latest.putIfAbsent(m.getInterlocuteurId(), m);
        List<MessageLivreur> convs = new ArrayList<>(latest.values());
        int from = Math.max(0, (offset - 1) * limit);
        List<MessageLivreur> pageItems = from >= convs.size() ? List.of() : convs.subList(from, Math.min(convs.size(), from + limit));
        List<Map<String, Object>> chat = pageItems.stream().map(m -> chatMap(m, type)).toList();
        return wrap("chat", chat, convs.size(), limit, offset);
    }

    /** Fil d'une conversation. */
    @GetMapping("/get-message/{type}/{id}")
    public Map<String, Object> getMessage(@AuthenticationPrincipal String email, @PathVariable String type,
                                          @PathVariable Long id,
                                          @RequestParam(defaultValue = "10") int limit,
                                          @RequestParam(defaultValue = "1") int offset) {
        User l = livreur(email);
        List<MessageLivreur> thread = messageRepository
                .findByLivreurIdAndInterlocuteurTypeAndInterlocuteurIdOrderByCreatedAtAsc(l.getId(), type, id);
        List<Map<String, Object>> messages = thread.stream().map(m -> messageMap(m, type)).toList();
        return wrap("message", messages, thread.size(), limit, offset);
    }

    /** Recherche de conversations par nom d'interlocuteur. Bare array. */
    @GetMapping("/search/{type}")
    public List<Map<String, Object>> search(@AuthenticationPrincipal String email, @PathVariable String type,
                                            @RequestParam(required = false) String search) {
        User l = livreur(email);
        List<MessageLivreur> all = messageRepository.findByLivreurIdAndInterlocuteurTypeOrderByCreatedAtDesc(l.getId(), type);
        Map<Long, MessageLivreur> latest = new LinkedHashMap<>();
        for (MessageLivreur m : all) latest.putIfAbsent(m.getInterlocuteurId(), m);
        return latest.values().stream()
                .filter(m -> search == null || search.isBlank() || matchesName(m.getInterlocuteurId(), search))
                .map(m -> chatMap(m, type))
                .toList();
    }

    /** Envoi d'un message (multipart : message, id destinataire, image[]). */
    @PostMapping(value = "/send-message/{type}", consumes = {"multipart/form-data"})
    public Map<String, Object> send(@AuthenticationPrincipal String email, @PathVariable String type,
                                    @RequestParam(value = "message", required = false) String message,
                                    @RequestParam("id") Long recipientId,
                                    @RequestParam(value = "image", required = false) MultipartFile[] images) {
        User l = livreur(email);
        List<String> urls = new ArrayList<>();
        if (images != null) {
            for (MultipartFile img : images) {
                if (img == null || img.isEmpty()) continue;
                try {
                    urls.add(minioService.uploadFile(img, "chatting"));
                } catch (Exception e) {
                    log.warn("Upload image chat livreur {} : {}", l.getId(), e.getMessage());
                }
            }
        }
        MessageLivreur saved = messageRepository.save(MessageLivreur.builder()
                .livreur(l).interlocuteurType(type).interlocuteurId(recipientId)
                .message(message).sentByDeliveryMan(true).seenByDeliveryMan(true)
                .attachments(urls).build());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", message != null ? message : "");
        out.put("time", saved.getCreatedAt() != null ? saved.getCreatedAt().format(TS) : LocalDateTime.now().format(TS));
        out.put("image", urls);
        return out;
    }

    // ---------- mapping ----------

    private Map<String, Object> chatMap(MessageLivreur m, String type) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", m.getId());
        c.put("user_id", m.getInterlocuteurId());
        c.put("seller_id", "seller".equalsIgnoreCase(type) ? m.getInterlocuteurId() : 0);
        c.put("message", m.getMessage());
        c.put("sent_by_customer", Boolean.FALSE);   // fragile : booléens
        c.put("sent_by_seller", Boolean.FALSE);
        c.put("sent_by_admin", Boolean.FALSE);
        c.put("seen_by_delivery_man", Boolean.TRUE.equals(m.getSeenByDeliveryMan()));
        c.put("created_at", m.getCreatedAt() != null ? m.getCreatedAt().format(TS) : null);
        c.put("customer", interlocuteurMap(m.getInterlocuteurId()));
        c.put("seller_info", interlocuteurMap(m.getInterlocuteurId()));
        return c;
    }

    private Map<String, Object> messageMap(MessageLivreur m, String type) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", m.getId());
        msg.put("message", m.getMessage());
        msg.put("sent_by_customer", Boolean.FALSE);
        msg.put("sent_by_seller", Boolean.FALSE);
        msg.put("sent_by_admin", Boolean.FALSE);
        msg.put("seen_by_delivery_man", Boolean.TRUE.equals(m.getSeenByDeliveryMan()));
        msg.put("created_at", m.getCreatedAt() != null ? m.getCreatedAt().format(TS) : null);
        msg.put("customer", interlocuteurMap(m.getInterlocuteurId()));
        msg.put("seller_info", interlocuteurMap(m.getInterlocuteurId()));
        msg.put("attachment", m.getAttachments() != null ? m.getAttachments() : List.of());
        return msg;
    }

    private Map<String, Object> interlocuteurMap(Long id) {
        Map<String, Object> m = new LinkedHashMap<>();
        User u = id != null ? userRepository.findById(id).orElse(null) : null;
        if (u == null) {
            m.put("id", id != null ? id : 0);
            m.put("name", "");
            m.put("f_name", "");
            m.put("l_name", "");
            m.put("phone", "");
            m.put("image", "");
            m.put("email", "");
            m.put("shops", List.of());
            return m;
        }
        m.put("id", u.getId());
        m.put("name", ((u.getPrenom() != null ? u.getPrenom() : "") + " " + (u.getNom() != null ? u.getNom() : "")).trim());
        m.put("f_name", nvl(u.getPrenom()));
        m.put("l_name", nvl(u.getNom()));
        m.put("phone", nvl(u.getTelephone()));
        m.put("image", nvl(u.getAvatar()));
        m.put("email", nvl(u.getEmail()));
        m.put("shops", List.of());
        return m;
    }

    private boolean matchesName(Long id, String search) {
        User u = id != null ? userRepository.findById(id).orElse(null) : null;
        if (u == null) return false;
        String name = ((u.getPrenom() != null ? u.getPrenom() : "") + " " + (u.getNom() != null ? u.getNom() : "")).toLowerCase();
        return name.contains(search.toLowerCase());
    }

    private static Map<String, Object> wrap(String key, List<?> items, int totalSize, int limit, int offset) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", totalSize);
        m.put("limit", String.valueOf(limit));
        m.put("offset", String.valueOf(offset));
        m.put(key, items);
        return m;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
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
