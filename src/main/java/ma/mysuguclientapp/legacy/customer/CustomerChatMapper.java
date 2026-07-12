package ma.mysuguclientapp.legacy.customer;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.ConversationUnifiee;
import ma.mysuguclientapp.entities.MessageUnifie;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.services.chat.ConversationService;
import ma.mysuguclientapp.services.chat.ParticipantResolver;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapping façade client (contrat 6valley §client chat, consommé par l'app MySuKu) : mêmes clés
 * JSON que la façade livreur (Task 5), mais {@code sent_by_*} sont des ENTIERS 1/0 (pas des
 * booléens) — l'app MySuKu teste `== 1` côté client.
 */
@Component
@RequiredArgsConstructor
public class CustomerChatMapper {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConversationService chat;
    private final ParticipantResolver resolver;

    /** INTEGER 1/0 flags — MySuKu teste `== 1`. */
    public Map<String, Object> message(MessageUnifie m) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", m.getId());
        o.put("message", m.getContenu());
        o.put("sent_by_customer", m.getExpediteurType() == ParticipantType.CUSTOMER ? 1 : 0);
        o.put("sent_by_seller", m.getExpediteurType() == ParticipantType.RESTAURANT ? 1 : 0);
        o.put("sent_by_admin", m.getExpediteurType() == ParticipantType.ADMIN ? 1 : 0);
        o.put("created_at", m.getCreatedAt() != null ? m.getCreatedAt().format(TS) : null);
        o.put("attachment", m.getAttachments() != null ? m.getAttachments() : List.of());
        return o;
    }

    public Map<String, Object> conversation(ConversationUnifiee c, ParticipantRef me, String type) {
        ParticipantRef other = chat.otherParty(c, me);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", c.getId());
        if ("seller".equals(type)) {
            o.put("seller_id", other.id());
            o.put("sellerInfo", resolver.restaurantInfo(other.id()));
        } else {
            o.put("delivery_man_id", other.id());
            o.put("deliveryMan", resolver.userInfo(other.id()));
        }
        o.put("message", c.getDernierMessage());
        o.put("unseen_message_count", chat.unseenCount(c, me));
        o.put("updated_at", c.getDernierMessageAt() != null ? c.getDernierMessageAt().format(TS) : null);
        return o;
    }
}
