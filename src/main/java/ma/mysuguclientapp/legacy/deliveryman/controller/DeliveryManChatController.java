package ma.mysuguclientapp.legacy.deliveryman.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.ConversationUnifiee;
import ma.mysuguclientapp.entities.MessageUnifie;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.chat.ConversationService;
import ma.mysuguclientapp.services.chat.ParticipantResolver;
import ma.mysuguclientapp.services.implementations.MinioService;
import ma.mysuguclientapp.services.implementations.CommandeAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static ma.mysuguclientapp.enumerations.ParticipantType.*;

/**
 * Chat livreur (contrat 6valley §5.14–§5.17). Rebranché (Task 5) sur le store unifié
 * ({@link ConversationService}) : mêmes chemins/forme JSON qu'avant, mais lit/écrit désormais
 * dans la même table que le chat client/restaurant (migration Task 4) — FCM + « vu » gérés par
 * le service unifié.
 * ⚠ `sent_by_customer/seller/admin` DOIVENT être des booléens JSON (fragile côté app).
 */
@RestController
@RequestMapping("/api/v2/delivery-man/messages")
@RequiredArgsConstructor
@Slf4j
public class DeliveryManChatController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final ConversationService chat;
    private final ParticipantResolver resolver;
    private final MinioService minioService;
    private final CommandeAccessService commandeAccessService;

    /** Liste des conversations d'un type : dernier message par interlocuteur. */
    @GetMapping("/list/{type}")
    public Map<String, Object> list(@AuthenticationPrincipal String email, @PathVariable String type,
                                    @RequestParam(defaultValue = "10") int limit,
                                    @RequestParam(defaultValue = "1") int offset) {
        User l = livreur(email);
        ParticipantRef me = livreurRef(l);
        ParticipantType otherType = mapType(type);
        List<ConversationUnifiee> convs = otherType == null ? List.of() : chat.conversationsFor(me, otherType).stream()
                .filter(c -> commandeAccessService.canAccessConversation(me, chat.otherParty(c, me))).toList();
        int from = Math.max(0, (offset - 1) * limit);
        List<ConversationUnifiee> pageItems = from >= convs.size() ? List.of() : convs.subList(from, Math.min(convs.size(), from + limit));
        List<Map<String, Object>> items = pageItems.stream().map(c -> chatMap(c, me, type)).toList();
        return wrap("chat", items, convs.size(), limit, offset);
    }

    /** Fil d'une conversation (marque les messages reçus comme vus). */
    @GetMapping("/get-message/{type}/{id}")
    public Map<String, Object> getMessage(@AuthenticationPrincipal String email, @PathVariable String type,
                                          @PathVariable Long id,
                                          @RequestParam(defaultValue = "10") int limit,
                                          @RequestParam(defaultValue = "1") int offset) {
        User l = livreur(email);
        ParticipantRef me = livreurRef(l);
        ParticipantType otherType = mapType(type);
        if (otherType == null) return wrap("message", List.of(), 0, limit, offset);
        ParticipantRef other = new ParticipantRef(otherType, id);
        commandeAccessService.requireConversationAccess(me, other);
        List<MessageUnifie> thread = chat.thread(me, other);
        List<Map<String, Object>> messages = thread.stream().map(m -> messageMap(m, me, other)).toList();
        return wrap("message", messages, thread.size(), limit, offset);
    }

    /** Recherche de conversations par nom d'interlocuteur. Bare array. */
    @GetMapping("/search/{type}")
    public List<Map<String, Object>> search(@AuthenticationPrincipal String email, @PathVariable String type,
                                            @RequestParam(required = false) String search) {
        User l = livreur(email);
        ParticipantRef me = livreurRef(l);
        ParticipantType otherType = mapType(type);
        if (otherType == null) return List.of();
        return chat.conversationsFor(me, otherType).stream()
                .filter(c -> commandeAccessService.canAccessConversation(me, chat.otherParty(c, me)))
                .filter(c -> search == null || search.isBlank() || matchesName(chat.otherParty(c, me), search))
                .map(c -> chatMap(c, me, type))
                .toList();
    }

    /** Envoi d'un message (multipart : message, id destinataire, image[]). */
    @PostMapping(value = "/send-message/{type}", consumes = {"multipart/form-data"})
    public Map<String, Object> send(@AuthenticationPrincipal String email, @PathVariable String type,
                                    @RequestParam(value = "message", required = false) String message,
                                    @RequestParam("id") Long recipientId,
                                    @RequestParam(value = "image", required = false) MultipartFile[] images) {
        User l = livreur(email);
        ParticipantRef me = livreurRef(l);
        ParticipantType otherType = mapType(type);
        if (otherType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type interlocuteur inconnu : " + type);
        }
        ParticipantRef other = new ParticipantRef(otherType, recipientId);
        commandeAccessService.requireConversationAccess(me, other);
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
        MessageUnifie saved = chat.append(me, other, message, urls);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", message != null ? message : "");
        out.put("time", saved.getCreatedAt() != null ? saved.getCreatedAt().format(TS) : LocalDateTime.now().format(TS));
        out.put("image", urls);
        return out;
    }

    // ---------- mapping ----------

    private Map<String, Object> chatMap(ConversationUnifiee c, ParticipantRef me, String type) {
        ParticipantRef other = chat.otherParty(c, me);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", c.getId());
        out.put("order_id", c.getCommandeId());
        out.put("user_id", other.id());
        out.put("seller_id", "seller".equalsIgnoreCase(type) ? other.id() : 0);
        out.put("message", c.getDernierMessage());
        ParticipantType lastSender = c.getDernierExpediteurType();
        out.put("sent_by_customer", lastSender == CUSTOMER);
        out.put("sent_by_seller", lastSender == RESTAURANT);
        out.put("sent_by_admin", lastSender == ADMIN);
        out.put("seen_by_delivery_man", chat.unseenCount(c, me) == 0);
        LocalDateTime at = c.getDernierMessageAt() != null ? c.getDernierMessageAt() : c.getCreatedAt();
        out.put("created_at", at != null ? at.format(TS) : null);
        Map<String, Object> info = participantInfo(other);
        out.put("customer", info);
        out.put("seller_info", info);
        out.put("unseen_message_count", chat.unseenCount(c, me));
        return out;
    }

    private Map<String, Object> messageMap(MessageUnifie m, ParticipantRef me, ParticipantRef other) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", m.getId());
        msg.put("order_id", m.getCommandeId());
        msg.put("message", m.getContenu());
        boolean mine = m.getExpediteurType() == me.type() && Objects.equals(m.getExpediteurId(), me.id());
        msg.put("sent_by_customer", !mine && m.getExpediteurType() == CUSTOMER);
        msg.put("sent_by_seller", !mine && m.getExpediteurType() == RESTAURANT);
        msg.put("sent_by_admin", !mine && m.getExpediteurType() == ADMIN);
        msg.put("seen_by_delivery_man", mine || m.isSeen());
        msg.put("created_at", m.getCreatedAt() != null ? m.getCreatedAt().format(TS) : null);
        Map<String, Object> info = participantInfo(other);
        msg.put("customer", info);
        msg.put("seller_info", info);
        msg.put("attachment", m.getAttachments() != null ? m.getAttachments() : List.of());
        return msg;
    }

    private Map<String, Object> participantInfo(ParticipantRef ref) {
        if (ref.type() == RESTAURANT) return resolver.restaurantInfo(ref.id());
        return resolver.userInfo(ref.id());
    }

    private boolean matchesName(ParticipantRef ref, String search) {
        Object name = participantInfo(ref).get("name");
        return name != null && name.toString().toLowerCase().contains(search.toLowerCase());
    }

    private static Map<String, Object> wrap(String key, List<?> items, int totalSize, int limit, int offset) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", totalSize);
        m.put("limit", String.valueOf(limit));
        m.put("offset", String.valueOf(offset));
        m.put(key, items);
        return m;
    }

    private ParticipantType mapType(String type) {
        if (type == null) return null;
        return switch (type.toLowerCase()) {
            case "customer" -> CUSTOMER;
            case "seller" -> RESTAURANT;
            case "admin" -> ADMIN;
            default -> null;
        };
    }

    private ParticipantRef livreurRef(User l) {
        return new ParticipantRef(LIVREUR, l.getId());
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
