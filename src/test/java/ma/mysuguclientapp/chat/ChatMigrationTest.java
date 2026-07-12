package ma.mysuguclientapp.chat;

import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.Conversation;
import ma.mysuguclientapp.entities.MessageChat;
import ma.mysuguclientapp.entities.MessageLivreur;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.chat.ChatMigrationRunner;
import ma.mysuguclientapp.repositories.ConversationUnifieeRepository;
import ma.mysuguclientapp.repositories.MessageLivreurRepository;
import ma.mysuguclientapp.repositories.MessageUnifieRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatMigrationRunner.class)
class ChatMigrationTest {
    @Autowired MessageLivreurRepository livreurRepo;
    @Autowired ConversationUnifieeRepository convs;
    @Autowired MessageUnifieRepository msgs;
    @Autowired ChatMigrationRunner runner;
    @Autowired TestEntityManager em;

    @Test
    void migrates_livreur_messages_into_unified_store() {
        User livreur = new User();
        livreur.setEmail("livreur@migration-test.mysugu.ma");
        livreur.setPassword("hashed");
        livreur.setNom("Traore");
        livreur.setPrenom("Amadou");
        livreur.setRole(UserRole.LIVREUR);
        em.persistAndFlush(livreur);

        livreurRepo.save(MessageLivreur.builder()
            .livreur(livreur)
            .interlocuteurType("customer").interlocuteurId(7L)
            .message("salut").sentByDeliveryMan(true).seenByDeliveryMan(true)
            .attachments(List.of()).build());

        runner.migrateOnce();

        assertEquals(1, convs.count());
        var c = convs.findAll().get(0);
        assertTrue((c.getPartyAType() == ParticipantType.CUSTOMER) || (c.getPartyBType() == ParticipantType.CUSTOMER));
        assertTrue((c.getPartyAType() == ParticipantType.LIVREUR) || (c.getPartyBType() == ParticipantType.LIVREUR));
        assertEquals(1, msgs.count());
        assertEquals(ParticipantType.LIVREUR, msgs.findAll().get(0).getExpediteurType());
    }

    @Test
    void migrates_system_b_messages_into_unified_store() {
        User client = new User();
        client.setEmail("client@migration-test.mysugu.ma");
        client.setPassword("hashed");
        client.setNom("Diallo");
        client.setPrenom("Fatou");
        client.setRole(UserRole.CLIENT);
        em.persistAndFlush(client);

        User owner = new User();
        owner.setEmail("owner@migration-test.mysugu.ma");
        owner.setPassword("hashed");
        owner.setNom("Sow");
        owner.setPrenom("Ibrahima");
        owner.setRole(UserRole.RESTAURANT_OWNER);
        em.persistAndFlush(owner);

        Restaurant restaurant = new Restaurant();
        restaurant.setNom("Chez Fatou");
        restaurant.setOwner(owner);
        em.persistAndFlush(restaurant);

        Commande commande = new Commande();
        commande.setNumeroCommande("CMD-MIGRATION-TEST-1");
        commande.setClient(client);
        commande.setRestaurant(restaurant);
        commande.setStatut(StatutCommande.EN_ATTENTE);
        commande.setMontantTotal(BigDecimal.TEN);
        em.persistAndFlush(commande);

        Conversation conversation = Conversation.builder()
                .client(client).restaurant(restaurant).commande(commande).build();
        em.persistAndFlush(conversation);

        MessageChat fromClient = MessageChat.builder()
                .conversation(conversation).expediteur(client).contenu("Bonjour, ou en est ma commande ?")
                .lu(true).build();
        em.persistAndFlush(fromClient);

        MessageChat fromRestaurant = MessageChat.builder()
                .conversation(conversation).expediteur(owner).contenu("Elle arrive !")
                .imageUrl("https://cdn.mysugu.ma/chat/photo.jpg").lu(false).build();
        em.persistAndFlush(fromRestaurant);

        runner.migrateOnce();

        assertEquals(1, convs.count());
        var c = convs.findAll().get(0);
        assertTrue((c.getPartyAType() == ParticipantType.CUSTOMER && c.getPartyAId().equals(client.getId()))
                || (c.getPartyBType() == ParticipantType.CUSTOMER && c.getPartyBId().equals(client.getId())));
        assertTrue((c.getPartyAType() == ParticipantType.RESTAURANT && c.getPartyAId().equals(restaurant.getId()))
                || (c.getPartyBType() == ParticipantType.RESTAURANT && c.getPartyBId().equals(restaurant.getId())));
        assertEquals(commande.getId(), c.getCommandeId());

        assertEquals(2, msgs.count());
        var migratedMessages = msgs.findAll();

        var clientMsg = migratedMessages.stream()
                .filter(m -> "Bonjour, ou en est ma commande ?".equals(m.getContenu())).findFirst().orElseThrow();
        assertEquals(ParticipantType.CUSTOMER, clientMsg.getExpediteurType());
        assertEquals(client.getId(), clientMsg.getExpediteurId());
        assertTrue(clientMsg.isSeen());
        assertTrue(clientMsg.getAttachments().isEmpty());

        var restaurantMsg = migratedMessages.stream()
                .filter(m -> "Elle arrive !".equals(m.getContenu())).findFirst().orElseThrow();
        assertEquals(ParticipantType.RESTAURANT, restaurantMsg.getExpediteurType());
        assertEquals(restaurant.getId(), restaurantMsg.getExpediteurId());
        assertFalse(restaurantMsg.isSeen());
        assertEquals(List.of("https://cdn.mysugu.ma/chat/photo.jpg"), restaurantMsg.getAttachments());
    }

    @Test
    void migration_is_idempotent_when_run_twice() {
        User livreur = new User();
        livreur.setEmail("livreur2@migration-test.mysugu.ma");
        livreur.setPassword("hashed");
        livreur.setNom("Traore");
        livreur.setPrenom("Sekou");
        livreur.setRole(UserRole.LIVREUR);
        em.persistAndFlush(livreur);

        livreurRepo.save(MessageLivreur.builder()
            .livreur(livreur)
            .interlocuteurType("seller").interlocuteurId(9L)
            .message("bonjour").sentByDeliveryMan(true).seenByDeliveryMan(false)
            .attachments(List.of()).build());

        runner.migrateOnce();
        long convCountAfterFirstRun = convs.count();
        long msgCountAfterFirstRun = msgs.count();
        assertEquals(1, convCountAfterFirstRun);
        assertEquals(1, msgCountAfterFirstRun);

        runner.migrateOnce();

        assertEquals(convCountAfterFirstRun, convs.count());
        assertEquals(msgCountAfterFirstRun, msgs.count());
    }
}
