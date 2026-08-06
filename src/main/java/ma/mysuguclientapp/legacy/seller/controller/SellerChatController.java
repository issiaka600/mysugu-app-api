package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.ConversationUnifiee;
import ma.mysuguclientapp.entities.MessageUnifie;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.legacy.seller.SellerContext;
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
 * Chat vendeur (contrat 6valley app vendeur : /api/v3/seller/messages/*). Rebranché sur le store
 * unifié ({@link ConversationService}) — mêmes conversations/messages que les côtés client et
 * livreur. Le vendeur est TOUJOURS identifié comme (RESTAURANT, restaurantId), exactement comme
 * les clients/livreurs l'adressent, donc les fils coïncident.
 * ⚠ `sent_by_*` / `seen_by_seller` DOIVENT être des booléens JSON (fragile côté app).
 * Le part fichier envoyé par l'app est nommé `image[]` (et non `image`).
 */
@RestController
@RequestMapping("/api/v3/seller/messages")
@RequiredArgsConstructor
@Slf4j
public class SellerChatController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SellerContext sellerContext;
    private final ConversationService chat;
    private final ParticipantResolver resolver;
    private final MinioService minioService;
    private final CommandeAccessService commandeAccessService;

    /** Liste des conversations d'un type (customer | delivery-man | admin). */
    @GetMapping("/list/{type}")
    public Map<String, Object> list(@AuthenticationPrincipal String email, @PathVariable String type,
                                    @RequestParam(defaultValue = "30") int limit,
                                    @RequestParam(defaultValue = "1") int offset) {
        ParticipantRef me = me(email);
        ParticipantType otherType = mapType(type);
        List<ConversationUnifiee> convs = otherType == null ? List.of() : chat.conversationsFor(me, otherType).stream()
                .filter(c -> commandeAccessService.canAccessConversation(me, chat.otherParty(c, me))).toList();
        int from = Math.max(0, (offset - 1) * limit);
        List<ConversationUnifiee> pageItems = from >= convs.size()
                ? List.of() : convs.subList(from, Math.min(convs.size(), from + limit));
        List<Map<String, Object>> items = pageItems.stream().map(c -> chatMap(c, me, type)).toList();
        return wrap("chat", items, convs.size(), limit, offset);
    }

    /** Fil d'une conversation (marque les messages reçus comme vus). */
    @GetMapping("/get-message/{type}/{id}")
    public Map<String, Object> getMessage(@AuthenticationPrincipal String email, @PathVariable String type,
                                          @PathVariable Long id,
                                          @RequestParam(defaultValue = "30") int limit,
                                          @RequestParam(defaultValue = "1") int offset) {
        ParticipantRef me = me(email);
        ParticipantType otherType = mapType(type);
        if (otherType == null) return wrap("message", List.of(), 0, limit, offset);
        ParticipantRef other = new ParticipantRef(otherType, id);
        commandeAccessService.requireConversationAccess(me, other);
        List<MessageUnifie> thread = chat.thread(me, other);
        List<Map<String, Object>> messages = thread.stream().map(m -> messageMap(m, me, other, type)).toList();
        return wrap("message", messages, thread.size(), limit, offset);
    }

    /** Recherche de conversations par nom d'interlocuteur. Renvoie un tableau brut. */
    @GetMapping("/search/{type}")
    public List<Map<String, Object>> search(@AuthenticationPrincipal String email, @PathVariable String type,
                                            @RequestParam(required = false) String search) {
        ParticipantRef me = me(email);
        ParticipantType otherType = mapType(type);
        if (otherType == null) return List.of();
        return chat.conversationsFor(me, otherType).stream()
                .filter(c -> commandeAccessService.canAccessConversation(me, chat.otherParty(c, me)))
                .filter(c -> search == null || search.isBlank() || matchesName(chat.otherParty(c, me), search))
                .map(c -> chatMap(c, me, type))
                .toList();
    }

    /** Envoi d'un message (multipart : message, id destinataire, image[]). */
    @PostMapping(value = "/send/{type}", consumes = {"multipart/form-data"})
    public Map<String, Object> send(@AuthenticationPrincipal String email, @PathVariable String type,
                                    @RequestParam(value = "message", required = false) String message,
                                    @RequestParam("id") Long recipientId,
                                    @RequestParam(value = "image[]", required = false) MultipartFile[] images) {
        ParticipantRef me = me(email);
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
                    log.warn("Upload image chat vendeur (resto {}) : {}", me.id(), e.getMessage());
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

    // ---------- mapping (forme JSON attendue par l'app vendeur) ----------

    private Map<String, Object> chatMap(ConversationUnifiee c, ParticipantRef me, String type) {
        ParticipantRef other = chat.otherParty(c, me);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", c.getId());
        out.put("order_id", c.getCommandeId());
        putCounterpartIds(out, other);
        out.put("message", c.getDernierMessage());
        ParticipantType lastSender = c.getDernierExpediteurType();
        out.put("sent_by_customer", lastSender == CUSTOMER);
        out.put("sent_by_delivery_man", lastSender == LIVREUR);
        out.put("sent_by_admin", lastSender == ADMIN);
        out.put("seen_by_seller", chat.unseenCount(c, me) == 0);
        LocalDateTime at = c.getDernierMessageAt() != null ? c.getDernierMessageAt() : c.getCreatedAt();
        String ts = at != null ? at.format(TS) : null;
        out.put("created_at", ts);
        out.put("updated_at", ts);
        putCounterpartInfo(out, other);
        out.put("unseen_message_count", chat.unseenCount(c, me));
        return out;
    }

    private Map<String, Object> messageMap(MessageUnifie m, ParticipantRef me, ParticipantRef other, String type) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", m.getId());
        msg.put("order_id", m.getCommandeId());
        putCounterpartIds(msg, other);
        msg.put("message", m.getContenu());
        boolean mine = m.getExpediteurType() == me.type() && Objects.equals(m.getExpediteurId(), me.id());
        msg.put("sent_by_seller", mine);
        msg.put("sent_by_customer", !mine && m.getExpediteurType() == CUSTOMER);
        msg.put("sent_by_delivery_man", !mine && m.getExpediteurType() == LIVREUR);
        msg.put("sent_by_admin", !mine && m.getExpediteurType() == ADMIN);
        msg.put("seen_by_seller", mine || m.isSeen());
        String ts = m.getCreatedAt() != null ? m.getCreatedAt().format(TS) : null;
        msg.put("created_at", ts);
        msg.put("updated_at", ts);
        putCounterpartInfo(msg, other);
        msg.put("attachment", m.getAttachments() != null ? m.getAttachments() : List.of());
        return msg;
    }

    /** Renseigne user_id / delivery_man_id / admin_id selon le type de l'interlocuteur. */
    private void putCounterpartIds(Map<String, Object> out, ParticipantRef other) {
        out.put("user_id", other.type() == CUSTOMER ? other.id() : null);
        out.put("delivery_man_id", other.type() == LIVREUR ? other.id() : null);
        out.put("admin_id", other.type() == ADMIN ? other.id() : null);
    }

    /** Renseigne l'objet imbriqué customer / delivery_man / admin selon le type. */
    private void putCounterpartInfo(Map<String, Object> out, ParticipantRef other) {
        Map<String, Object> info = participantInfo(other);
        out.put("customer", other.type() == CUSTOMER ? info : null);
        out.put("delivery_man", other.type() == LIVREUR ? info : null);
        out.put("admin", other.type() == ADMIN ? info : null);
    }

    private Map<String, Object> participantInfo(ParticipantRef ref) {
        if (ref.type() == ADMIN) {
            Map<String, Object> admin = new LinkedHashMap<>();
            admin.put("id", ref.id());
            admin.put("name", "Admin");
            admin.put("phone", "");
            admin.put("image", "");
            return admin;
        }
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
            case "delivery-man", "delivery_man", "deliveryman" -> LIVREUR;
            case "admin" -> ADMIN;
            default -> null;
        };
    }

    private ParticipantRef me(String email) {
        Restaurant r = sellerContext.currentRestaurant(email); // 403 non-vendeur / 404 sans restaurant
        return new ParticipantRef(RESTAURANT, r.getId());
    }
}
