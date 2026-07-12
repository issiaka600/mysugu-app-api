package ma.mysuguclientapp.legacy.customer.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.ConversationUnifiee;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.customer.CustomerChatMapper;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.chat.ConversationService;
import ma.mysuguclientapp.services.implementations.MinioService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat client (contrat 6valley — consommé par l'app MySuKu) : {@code /api/v1/customer/chat/*}.
 * Façade NEUVE (Task 7) posée directement sur le store unifié ({@link ConversationService}),
 * sans passer par une ancienne table legacy. Mêmes conventions que
 * {@link ma.mysuguclientapp.legacy.deliveryman.controller.DeliveryManChatController} (Task 5),
 * mais {@code sent_by_*} sont des ENTIERS JSON 1/0 (l'app MySuKu teste `== 1`), pas des booléens.
 * <p>
 * {@code {type}} : {@code seller} → RESTAURANT (id=0 → ADMIN, canal support) ; {@code delivery-man}
 * → LIVREUR. Le principal doit être un utilisateur CLIENT.
 */
@RestController
@RequestMapping("/api/v1/customer/chat")
@RequiredArgsConstructor
@Slf4j
public class CustomerChatController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConversationService chat;
    private final CustomerChatMapper mapper;
    private final UserRepository userRepository;
    private final MinioService minioService;

    @GetMapping("/list/{type}")
    public Map<String, Object> list(@AuthenticationPrincipal String email, @PathVariable String type,
                                    @RequestParam(defaultValue = "10") int limit,
                                    @RequestParam(defaultValue = "1") int offset) {
        ParticipantRef me = me(email);
        List<ConversationUnifiee> all = chat.conversationsFor(me, counterpartType(type));
        int from = Math.max(0, (offset - 1) * limit);
        List<ConversationUnifiee> page = from >= all.size() ? List.of()
                : all.subList(from, Math.min(all.size(), from + limit));
        List<Map<String, Object>> chatList = page.stream().map(c -> mapper.conversation(c, me, type)).toList();
        return wrap("chat", chatList, all.size(), limit, offset);
    }

    @GetMapping("/get-messages/{type}/{id}")
    public Map<String, Object> messages(@AuthenticationPrincipal String email, @PathVariable String type,
                                        @PathVariable Long id,
                                        @RequestParam(defaultValue = "30") int limit,
                                        @RequestParam(defaultValue = "1") int offset) {
        ParticipantRef me = me(email);
        var thread = chat.thread(me, other(type, id));
        List<Map<String, Object>> out = thread.stream().map(mapper::message).toList();
        return wrap("message", out, out.size(), limit, offset);
    }

    @PostMapping(value = "/send-message/{type}", consumes = {"multipart/form-data"})
    public Map<String, Object> send(@AuthenticationPrincipal String email, @PathVariable String type,
                                    @RequestParam("id") Long id,
                                    @RequestParam(value = "message", required = false) String message,
                                    @RequestParam(value = "image", required = false) MultipartFile[] images) {
        ParticipantRef me = me(email);
        List<String> urls = new ArrayList<>();
        if (images != null) {
            for (MultipartFile f : images) {
                if (f == null || f.isEmpty()) continue;
                try {
                    urls.add(minioService.uploadFile(f, "chatting"));
                } catch (Exception e) {
                    log.warn("Upload image chat client {} : {}", me.id(), e.getMessage());
                }
            }
        }
        var saved = chat.append(me, other(type, id), message, urls);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("message", message != null ? message : "");
        o.put("time", saved.getCreatedAt() != null ? saved.getCreatedAt().format(TS) : LocalDateTime.now().format(TS));
        o.put("image", urls);
        return o;
    }

    @PostMapping("/seen-message/{type}")
    public Map<String, Object> seen(@AuthenticationPrincipal String email, @PathVariable String type,
                                    @RequestParam("id") Long id) {
        chat.markSeen(me(email), other(type, id));
        return Map.of("message", "Successfully seen");
    }

    // ---------- helpers ----------

    private ParticipantRef me(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié"));
        if (u.getRole() != UserRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé au client");
        }
        return new ParticipantRef(ParticipantType.CUSTOMER, u.getId());
    }

    private ParticipantType counterpartType(String type) {
        return "seller".equals(type) ? ParticipantType.RESTAURANT : ParticipantType.LIVREUR;
    }

    private ParticipantRef other(String type, Long id) {
        if ("seller".equals(type)) {
            return (id != null && id == 0L) ? new ParticipantRef(ParticipantType.ADMIN, 0L)
                    : new ParticipantRef(ParticipantType.RESTAURANT, id);
        }
        return new ParticipantRef(ParticipantType.LIVREUR, id);
    }

    private static Map<String, Object> wrap(String key, List<?> items, int total, int limit, int offset) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", total);
        m.put("limit", limit);
        m.put("offset", offset);
        m.put(key, items);
        return m;
    }
}
