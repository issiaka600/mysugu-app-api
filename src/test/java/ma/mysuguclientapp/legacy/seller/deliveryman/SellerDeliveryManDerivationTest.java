package ma.mysuguclientapp.legacy.seller.deliveryman;

import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.LigneCommande;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3e.1: derivation queries on CommandeRepository — the "roster" of livreurs who actually
 * served a restaurant is DERIVED (read-only) from Commande rows, since vendor->livreur ownership
 * is NOT native (umbrella §4 GAP; 3e SCOPE DECISION). Plan 2026-07-10-vendor-3e-deliveryman.md
 * task 3e.1.
 */
@SpringBootTest
@TestPropertySource(properties = {"dispatch.auto.enabled=false"})
class SellerDeliveryManDerivationTest {

    @Autowired UserRepository userRepo;
    @Autowired RestaurantRepository restoRepo;
    @Autowired PlatRepository platRepo;
    @Autowired CommandeRepository commandeRepo;
    @Autowired PasswordEncoder encoder;

    private Long ownerMineId, ownerOtherId, clientId, livreurAId, livreurBId, restoMineId, restoOtherId, platMineId, platOtherId;
    private Long commandeA1Id, commandeA2Id, commandeBOtherId;

    @BeforeEach
    void seed() {
        ownerMineId = newUser("dm-deriv-owner1-", UserRole.RESTAURANT_OWNER).getId();
        ownerOtherId = newUser("dm-deriv-owner2-", UserRole.RESTAURANT_OWNER).getId();
        User client = newUser("dm-deriv-client-", UserRole.CLIENT);
        clientId = client.getId();
        livreurAId = newUser("dm-deriv-livreurA-", UserRole.LIVREUR).getId();
        livreurBId = newUser("dm-deriv-livreurB-", UserRole.LIVREUR).getId();

        Restaurant restoMine = newResto("Boutique Mine", userRepo.findById(ownerMineId).orElseThrow());
        restoMineId = restoMine.getId();
        Restaurant restoOther = newResto("Boutique Autre", userRepo.findById(ownerOtherId).orElseThrow());
        restoOtherId = restoOther.getId();

        Plat platMine = newPlat("Tajine", restoMine);
        platMineId = platMine.getId();
        Plat platOther = newPlat("Pizza", restoOther);
        platOtherId = platOther.getId();

        User livreurA = userRepo.findById(livreurAId).orElseThrow();
        User livreurB = userRepo.findById(livreurBId).orElseThrow();

        commandeA1Id = newCommande(client, restoMine, platMine, livreurA).getId();
        commandeA2Id = newCommande(client, restoMine, platMine, livreurA).getId();
        commandeBOtherId = newCommande(client, restoOther, platOther, livreurB).getId();
    }

    @AfterEach
    void cleanup() {
        commandeRepo.findById(commandeA1Id).ifPresent(commandeRepo::delete);
        commandeRepo.findById(commandeA2Id).ifPresent(commandeRepo::delete);
        commandeRepo.findById(commandeBOtherId).ifPresent(commandeRepo::delete);
        platRepo.findById(platMineId).ifPresent(platRepo::delete);
        platRepo.findById(platOtherId).ifPresent(platRepo::delete);
        restoRepo.findById(restoMineId).ifPresent(restoRepo::delete);
        restoRepo.findById(restoOtherId).ifPresent(restoRepo::delete);
        userRepo.findById(clientId).ifPresent(userRepo::delete);
        userRepo.findById(livreurAId).ifPresent(userRepo::delete);
        userRepo.findById(livreurBId).ifPresent(userRepo::delete);
        userRepo.findById(ownerMineId).ifPresent(userRepo::delete);
        userRepo.findById(ownerOtherId).ifPresent(userRepo::delete);
    }

    @Test
    void findDistinctLivreursByRestaurant_returns_only_livreurs_who_served_this_restaurant() {
        List<User> roster = commandeRepo.findDistinctLivreursByRestaurant(restoMineId);
        assertThat(roster).extracting(User::getId).containsExactly(livreurAId);
    }

    @Test
    void findByLivreurAndRestaurant_returns_only_matching_commandes() {
        List<Commande> mine = commandeRepo.findByLivreurAndRestaurant(livreurAId, restoMineId);
        assertThat(mine).extracting(Commande::getId).containsExactlyInAnyOrder(commandeA1Id, commandeA2Id);
    }

    @Test
    void findByLivreurAndRestaurant_returns_empty_when_livreur_never_served_that_restaurant() {
        List<Commande> none = commandeRepo.findByLivreurAndRestaurant(livreurAId, restoOtherId);
        assertThat(none).isEmpty();
    }

    private Commande newCommande(User client, Restaurant r, Plat p, User livreur) {
        Commande c = new Commande();
        c.setNumeroCommande("ORD-DM-DERIV-" + System.nanoTime() + "-" + Math.random());
        c.setClient(client);
        c.setRestaurant(r);
        c.setLivreur(livreur);
        c.setStatut(StatutCommande.LIVREE);
        c.setStatutPaiement(StatutPaiement.PAYE);
        c.setMontantTotal(new BigDecimal("100.00"));
        c.setMontantFinal(new BigDecimal("100.00"));

        LigneCommande ligne = new LigneCommande();
        ligne.setCommande(c);
        ligne.setPlat(p);
        ligne.setQuantite(1);
        ligne.setPrixUnitaire(p.getPrix());
        ligne.setMontantTotal(p.getPrix());
        c.setLignesCommande(List.of(ligne));

        return commandeRepo.save(c);
    }

    private User newUser(String prefix, UserRole role) {
        User u = new User();
        u.setEmail(prefix + System.nanoTime() + "@test.mysugu");
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("N"); u.setPrenom("P");
        u.setRole(role);
        u.setIsActive(true);
        return userRepo.save(u);
    }

    private Restaurant newResto(String nom, User owner) {
        Restaurant r = new Restaurant();
        r.setNom(nom);
        r.setDescription("desc");
        r.setTempsLivraisonMoyen(30);
        r.setOwner(owner);
        r.setIsActive(true);
        r.setLocalisation(new Localisation(1.0, 2.0, "rue", "Casa", "20000", "Maroc"));
        return restoRepo.save(r);
    }

    private Plat newPlat(String nom, Restaurant r) {
        Plat p = new Plat();
        p.setNom(nom);
        p.setDescription("desc " + nom);
        p.setPrix(new BigDecimal("30.00"));
        p.setRestaurant(r);
        p.setIsAvailable(true);
        return platRepo.save(p);
    }
}
