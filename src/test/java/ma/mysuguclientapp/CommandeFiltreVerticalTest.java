package ma.mysuguclientapp;

import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filtre par verticale sur la liste des commandes.
 *
 * Le jeu de données comporte volontairement un établissement à {@code vertical = NULL} :
 * c'est la donnée historique qui a fait passer le bug de juin à travers la suite de tests,
 * parce que toutes les fixtures renseignaient la verticale. Chaque cas vérifie LES DEUX SENS —
 * ce qui doit sortir sort, et ce qui doit rester reste.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommandeFiltreVerticalTest {

    @Autowired CommandeService commandeService;
    @Autowired CommandeRepository commandeRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder encoder;
    @Autowired TransactionTemplate tx;

    private Long clientId;
    private Long restoHistoriqueId;
    private Long boutiqueId;
    private Long commandeHistoriqueId;
    private Long commandeBoutiqueId;

    @BeforeAll
    void setup() {
        User client = new User();
        client.setEmail("cmd-vertical-" + System.nanoTime() + "@test.mysugu");
        client.setPassword(encoder.encode("demo1234"));
        client.setNom("N"); client.setPrenom("P");
        client.setRole(UserRole.CLIENT);
        client.setIsActive(true);
        clientId = userRepository.save(client).getId();

        // Etablissement historique : vertical NON renseigne => NULL en base.
        Restaurant historique = new Restaurant();
        historique.setNom("Historique CmdVert " + System.nanoTime());
        historique.setIsActive(true);
        restoHistoriqueId = restaurantRepository.save(historique).getId();

        Restaurant boutique = new Restaurant();
        boutique.setNom("Epicerie CmdVert " + System.nanoTime());
        boutique.setIsActive(true);
        boutique.setVertical(Vertical.ALIMENTAIRE);
        boutiqueId = restaurantRepository.save(boutique).getId();

        commandeHistoriqueId = nouvelleCommande(client, restaurantRepository.findById(restoHistoriqueId).orElseThrow());
        commandeBoutiqueId = nouvelleCommande(client, restaurantRepository.findById(boutiqueId).orElseThrow());
    }

    @AfterAll
    void cleanup() {
        tx.executeWithoutResult(s -> {
            commandeRepository.deleteById(commandeHistoriqueId);
            commandeRepository.deleteById(commandeBoutiqueId);
            restaurantRepository.deleteById(restoHistoriqueId);
            restaurantRepository.deleteById(boutiqueId);
            userRepository.deleteById(clientId);
        });
    }

    @Test
    void filtreAlimentaire_sortLaBoutique_etLaisseLHistorique() {
        Page<CommandeDTO> alimentaire =
                commandeService.getAllCommandes(null, null, null, Vertical.ALIMENTAIRE, Pageable.unpaged());

        assertThat(alimentaire.getContent()).extracting(CommandeDTO::getId)
                .contains(commandeBoutiqueId)
                .doesNotContain(commandeHistoriqueId);
    }

    @Test
    void filtreRestaurant_rattrapeLesEtablissementsHistoriquesAVerticalNull() {
        Page<CommandeDTO> restaurants =
                commandeService.getAllCommandes(null, null, null, Vertical.RESTAURANT, Pageable.unpaged());

        // Ce qui doit rester reste : vertical NULL vaut RESTAURANT.
        assertThat(restaurants.getContent()).extracting(CommandeDTO::getId)
                .contains(commandeHistoriqueId)
                .doesNotContain(commandeBoutiqueId);
    }

    @Test
    void sansParametre_leComportementEstInchange() {
        Page<CommandeDTO> toutes =
                commandeService.getAllCommandes(null, null, null, null, Pageable.unpaged());

        assertThat(toutes.getContent()).extracting(CommandeDTO::getId)
                .contains(commandeHistoriqueId, commandeBoutiqueId);
    }

    @Test
    void leFiltreRestaurantIdResteOperant() {
        Page<CommandeDTO> uneSeule =
                commandeService.getAllCommandes(null, boutiqueId, null, null, Pageable.unpaged());

        assertThat(uneSeule.getContent()).extracting(CommandeDTO::getId)
                .contains(commandeBoutiqueId)
                .doesNotContain(commandeHistoriqueId);
    }

    private Long nouvelleCommande(User client, Restaurant r) {
        Commande c = new Commande();
        c.setNumeroCommande("ORD-CMDVERT-" + System.nanoTime());
        c.setClient(client);
        c.setRestaurant(r);
        c.setStatut(StatutCommande.LIVREE);
        c.setStatutPaiement(StatutPaiement.PAYE);
        c.setMontantTotal(new BigDecimal("100.00"));
        c.setMontantFinal(new BigDecimal("100.00"));
        return commandeRepository.save(c).getId();
    }
}
