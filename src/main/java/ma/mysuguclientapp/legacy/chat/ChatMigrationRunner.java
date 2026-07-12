package ma.mysuguclientapp.legacy.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.Conversation;
import ma.mysuguclientapp.entities.ConversationUnifiee;
import ma.mysuguclientapp.entities.MessageChat;
import ma.mysuguclientapp.entities.MessageLivreur;
import ma.mysuguclientapp.entities.MessageUnifie;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.repositories.ConversationRepository;
import ma.mysuguclientapp.repositories.ConversationUnifieeRepository;
import ma.mysuguclientapp.repositories.MessageChatRepository;
import ma.mysuguclientapp.repositories.MessageLivreurRepository;
import ma.mysuguclientapp.repositories.MessageUnifieRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One-shot migration of the legacy chat systems into the unified chat store.
 *
 * <p>System A: livreur &lt;-&gt; interlocuteur chat ({@link MessageLivreur} /
 * {@link MessageLivreurRepository}, feature added for the TikTak shim).
 * System B: client &lt;-&gt; restaurant chat ({@link Conversation} / {@link MessageChat} +
 * their repositories, pre-existing MESSAGERIE module).
 *
 * <p>Runs once at application startup (idempotent: skipped if {@code conversations_unifiees}
 * is already non-empty) and can also be invoked directly (e.g. from tests) via
 * {@link #migrateOnce()}. Legacy tables are NOT dropped here — see
 * {@code // TODO(Task 9): drop after cutover} on the legacy entities.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMigrationRunner implements ApplicationRunner {

    private final MessageLivreurRepository livreurRepo;
    private final ConversationRepository oldConvRepo;       // System B
    private final MessageChatRepository oldMsgRepo;         // System B
    private final ConversationUnifieeRepository convs;
    private final MessageUnifieRepository msgs;

    @Override
    public void run(ApplicationArguments args) {
        migrateOnce();
    }

    @Transactional
    public void migrateOnce() {
        if (convs.count() > 0) {
            log.info("Chat migration skipped (already populated).");
            return;
        }
        migrateLivreur();
        migrateSystemB();
        log.info("Chat migration done: {} conversations, {} messages", convs.count(), msgs.count());
    }

    private void migrateLivreur() {
        for (MessageLivreur ml : livreurRepo.findAll()) {
            ParticipantType otherType = switch (ml.getInterlocuteurType()) {
                case "customer" -> ParticipantType.CUSTOMER;
                case "seller" -> ParticipantType.RESTAURANT;
                case "admin" -> ParticipantType.ADMIN;
                default -> ParticipantType.CUSTOMER;
            };
            Long livreurId = ml.getLivreur() != null ? ml.getLivreur().getId() : null;
            if (livreurId == null) {
                continue;
            }
            ConversationUnifiee c = findOrCreate(ParticipantType.LIVREUR, livreurId, otherType, ml.getInterlocuteurId());
            saveMsg(c, ParticipantType.LIVREUR, livreurId, ml.getMessage(),
                    ml.getAttachments(), Boolean.TRUE.equals(ml.getSeenByDeliveryMan()), ml.getCreatedAt());
        }
    }

    private void migrateSystemB() {
        for (Conversation oc : oldConvRepo.findAll()) {
            Long clientId = oc.getClient() != null ? oc.getClient().getId() : null;
            Long restId = oc.getRestaurant() != null ? oc.getRestaurant().getId() : null;
            if (clientId == null || restId == null) {
                continue;
            }
            ConversationUnifiee c = findOrCreate(ParticipantType.CUSTOMER, clientId, ParticipantType.RESTAURANT, restId);
            if (oc.getCommande() != null) {
                c.setCommandeId(oc.getCommande().getId());
                convs.save(c);
            }
            for (MessageChat om : oldMsgRepo.findByConversationIdOrderByCreatedAtAsc(oc.getId())) {
                boolean fromClient = om.getExpediteur() != null && Objects.equals(om.getExpediteur().getId(), clientId);
                ParticipantType exp = fromClient ? ParticipantType.CUSTOMER : ParticipantType.RESTAURANT;
                Long expId = fromClient ? clientId : restId;
                List<String> att = om.getImageUrl() != null ? List.of(om.getImageUrl()) : List.of();
                saveMsg(c, exp, expId, om.getContenu(), att, Boolean.TRUE.equals(om.getLu()), om.getCreatedAt());
            }
        }
    }

    /** Canonicalizes (aT,aId)/(bT,bId) by (ordinal, id) before delegating to the fixed-order finder. */
    private ConversationUnifiee findOrCreate(ParticipantType aT, Long aId, ParticipantType bT, Long bId) {
        boolean swap = (aT.ordinal() > bT.ordinal()) || (aT == bT && aId != null && bId != null && aId > bId);
        ParticipantType p1t = swap ? bT : aT;
        Long p1i = swap ? bId : aId;
        ParticipantType p2t = swap ? aT : bT;
        Long p2i = swap ? aId : bId;
        return convs.findByParties(p1t, p1i, p2t, p2i).orElseGet(() ->
                convs.save(ConversationUnifiee.builder()
                        .partyAType(p1t).partyAId(p1i).partyBType(p2t).partyBId(p2i).build()));
    }

    private void saveMsg(ConversationUnifiee c, ParticipantType expT, Long expId, String contenu,
                          List<String> att, boolean seen, java.time.LocalDateTime createdAt) {
        MessageUnifie m = MessageUnifie.builder().conversationId(c.getId())
                .expediteurType(expT).expediteurId(expId).contenu(contenu)
                .attachments(att != null ? new ArrayList<>(att) : new ArrayList<>()).seen(seen).build();
        msgs.save(m);
        if (c.getDernierMessageAt() == null || (createdAt != null && createdAt.isAfter(c.getDernierMessageAt()))) {
            c.setDernierMessage(contenu);
            c.setDernierMessageAt(createdAt);
            c.setDernierExpediteurType(expT);
            c.setDernierExpediteurId(expId);
            convs.save(c);
        }
    }
}
