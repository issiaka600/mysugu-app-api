package ma.mysuguclientapp.services.chat;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.enumerations.TypeNotification;
import ma.mysuguclientapp.repositories.*;
import ma.mysuguclientapp.services.interfaces.NotificationService;
import ma.mysuguclientapp.services.implementations.CommandeAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ConversationUnifieeRepository convs;
    private final MessageUnifieRepository msgs;
    private final ParticipantResolver resolver;
    private final NotificationService notifications;
    private final CommandeAccessService commandeAccessService;

    @Transactional
    public MessageUnifie append(ParticipantRef from, ParticipantRef to, String contenu, List<String> attachments) {
        return append(from, to, contenu, attachments, null);
    }

    /**
     * Comme {@link #append(ParticipantRef, ParticipantRef, String, List)}, avec en plus un
     * {@code commandeId} optionnel à rattacher au message. La conversation conserve la dernière
     * commande concernée, afin que le clic sur une notification ouvre le bon contexte.
     */
    @Transactional
    public MessageUnifie append(ParticipantRef from, ParticipantRef to, String contenu, List<String> attachments,
                                 Long commandeId) {
        Long resolvedCommandeId = commandeId != null
                ? commandeId : commandeAccessService.resolveSharedCommandeId(from, to);
        commandeAccessService.requireConversationAccessForCommande(from, to, resolvedCommandeId);
        ConversationUnifiee c = findOrCreate(from, to, resolvedCommandeId);
        MessageUnifie m = msgs.save(MessageUnifie.builder()
                .conversationId(c.getId())
                .commandeId(resolvedCommandeId)
                .expediteurType(from.type()).expediteurId(from.id())
                .contenu(contenu)
                .attachments(attachments != null ? new ArrayList<>(attachments) : new ArrayList<>())
                .seen(false).build());
        c.setDernierMessage(ConversationUnifiee.truncateDernierMessage(contenu));
        c.setCommandeId(resolvedCommandeId);
        c.setDernierMessageAt(m.getCreatedAt() != null ? m.getCreatedAt() : LocalDateTime.now());
        c.setDernierExpediteurType(from.type());
        c.setDernierExpediteurId(from.id());
        convs.save(c);
        Long recipientUser = resolver.notifiableUserId(to);
        if (recipientUser != null) {
            try {
                Map<String, Object> sender = from.type() == ParticipantType.RESTAURANT
                        ? resolver.restaurantInfo(from.id()) : resolver.userInfo(from.id());
                notifications.envoyerNotificationMessage(recipientUser, String.valueOf(from.id()),
                        senderType(from.type()), String.valueOf(sender.get("name")), c.getId(),
                        resolvedCommandeId, totalUnseen(to));
            } catch (Exception ignored) { /* FCM best-effort */ }
        }
        return m;
    }

    @Transactional
    public List<MessageUnifie> thread(ParticipantRef me, ParticipantRef other) {
        commandeAccessService.requireConversationAccess(me, other);
        Optional<ConversationUnifiee> c = find(me, other);
        if (c.isEmpty()) return List.of();
        List<MessageUnifie> list = msgs.findByConversationIdOrderByCreatedAtAsc(c.get().getId());
        for (MessageUnifie m : list) {
            if (!isMine(m, me) && !m.isSeen()) { m.setSeen(true); msgs.save(m); }
        }
        return list;
    }

    @Transactional
    public void markSeen(ParticipantRef me, ParticipantRef other) {
        commandeAccessService.requireConversationAccess(me, other);
        find(me, other).ifPresent(c ->
            msgs.findByConversationIdOrderByCreatedAtAsc(c.getId()).forEach(m -> {
                if (!isMine(m, me) && !m.isSeen()) { m.setSeen(true); msgs.save(m); }
            }));
    }

    public List<ConversationUnifiee> conversationsFor(ParticipantRef me, ParticipantType counterpartType) {
        return convs.findByParticipant(me.type(), me.id()).stream()
            .filter(c -> otherParty(c, me).type() == counterpartType)
            .filter(c -> commandeAccessService.canAccessConversation(me, otherParty(c, me)))
            .toList();
    }

    public long unseenCount(ConversationUnifiee c, ParticipantRef me) {
        return msgs.countUnseenForParticipant(
                c.getId(), me.type(), me.id());
    }

    public long totalUnseen(ParticipantRef me) {
        return convs.findByParticipant(me.type(), me.id()).stream()
                .filter(c -> commandeAccessService.canAccessConversation(me, otherParty(c, me)))
                .mapToLong(c -> unseenCount(c, me))
                .sum();
    }

    public ParticipantRef otherParty(ConversationUnifiee c, ParticipantRef me) {
        ParticipantRef a = new ParticipantRef(c.getPartyAType(), c.getPartyAId());
        ParticipantRef b = new ParticipantRef(c.getPartyBType(), c.getPartyBId());
        return a.equals(me) ? b : a;
    }

    private boolean isMine(MessageUnifie m, ParticipantRef me) {
        return m.getExpediteurType() == me.type() && Objects.equals(m.getExpediteurId(), me.id());
    }

    private String senderType(ParticipantType type) {
        return switch (type) {
            case CUSTOMER -> "customer";
            case LIVREUR -> "delivery_man";
            case RESTAURANT -> "restaurant";
            case ADMIN -> "admin";
        };
    }

    private Optional<ConversationUnifiee> find(ParticipantRef x, ParticipantRef y) {
        ParticipantRef[] p = ParticipantRef.canonical(x, y);
        return convs.findByParties(p[0].type(), p[0].id(), p[1].type(), p[1].id());
    }

    private ConversationUnifiee findOrCreate(ParticipantRef x, ParticipantRef y, Long commandeId) {
        return find(x, y).orElseGet(() -> {
            ParticipantRef[] p = ParticipantRef.canonical(x, y);
            return convs.save(ConversationUnifiee.builder()
                .partyAType(p[0].type()).partyAId(p[0].id())
                .partyBType(p[1].type()).partyBId(p[1].id())
                .commandeId(commandeId).build());
        });
    }
}
