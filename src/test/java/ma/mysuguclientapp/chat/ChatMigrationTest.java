package ma.mysuguclientapp.chat;

import ma.mysuguclientapp.entities.MessageLivreur;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.ParticipantType;
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
}
