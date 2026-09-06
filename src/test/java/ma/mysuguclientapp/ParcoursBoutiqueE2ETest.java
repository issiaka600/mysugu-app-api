package ma.mysuguclientapp;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.mysuguclientapp.dtos.CommandeCreateDTO;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.LigneCommandeCreateDTO;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.ModeReceptionCommande;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import ma.mysuguclientapp.services.interfaces.PlatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prouve, de bout en bout, que le parcours d'achat en boutique (vertical ALIMENTAIRE) ne
 * nécessite aucun endpoint ni service dédié : catalogue interrogeable par rayon via
 * {@link PlatService}, commande créée/annulée via le même {@link CommandeService} que le
 * parcours restaurant, avec décrément/restitution de stock au bon moment.
 *
 * <p>Chaque test est autonome et {@code @Transactional} : la base de test étant persistante et
 * rejouable à l'infini, le rollback automatique en fin de test évite tout nettoyage manuel des
 * lignes de commande / alertes vendeur / notifications que la création d'une commande déclenche
 * en cascade. Les listeners qui poussent des notifications ou des alertes après commit
 * ({@code CommandeCreeeListener}, {@code AlerteCommandeVendeurService}) sont enregistrés en
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} : comme la transaction du test
 * n'est jamais committée, ils ne s'exécutent jamais — pas d'effet de bord résiduel à nettoyer.</p>
 */
@SpringBootTest
class ParcoursBoutiqueE2ETest {

    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PlatRepository platRepository;
    @Autowired UserRepository userRepository;
    @Autowired CommandeRepository commandeRepository;
    @Autowired CommandeService commandeService;
    @Autowired PlatService platService;
    @PersistenceContext EntityManager entityManager;

    private Restaurant creerBoutique() {
        Restaurant boutique = new Restaurant();
        boutique.setNom("Boutique E2E " + System.nanoTime());
        boutique.setIsActive(true);
        // autoCloseEnabled reste false (défaut) : isRestaurantOpenNow renvoie true sans horaires.
        boutique.setVertical(Vertical.ALIMENTAIRE);
        return restaurantRepository.save(boutique);
    }

    private Plat creerProduit(Restaurant boutique, String rayon, int stock) {
        Plat produit = new Plat();
        produit.setNom("Produit " + rayon + " " + System.nanoTime());
        produit.setPrix(new BigDecimal("120.00"));
        produit.setIsAvailable(true);
        produit.setQuantiteStock(stock);
        produit.setCategorieProduit(rayon);
        produit.setRestaurant(boutique);
        return platRepository.save(produit);
    }

    private User creerClient() {
        User client = new User();
        client.setEmail("client.e2e." + System.nanoTime() + "@mysugu.test");
        client.setPassword("motdepasse");
        client.setNom("Test");
        client.setPrenom("Client");
        client.setRole(UserRole.CLIENT);
        client.setIsActive(true);
        return userRepository.save(client);
    }

    private CommandeCreateDTO commandeDto(Long clientId, Long restaurantId, Long platId, int quantite) {
        var ligne = new LigneCommandeCreateDTO();
        ligne.setPlatId(platId);
        ligne.setQuantite(quantite);

        var dto = new CommandeCreateDTO();
        dto.setClientId(clientId);
        dto.setRestaurantId(restaurantId);
        dto.setLignes(List.of(ligne));
        // RETRAIT_SUR_PLACE : évite d'avoir à créer une zone de déploiement pour ce test.
        dto.setModeReception(ModeReceptionCommande.RETRAIT_SUR_PLACE.name());
        dto.setMethodePaiement("ESPECES");
        return dto;
    }

    /**
     * Le catalogue d'une boutique est navigable par rayon (paramètre {@code categorieProduit}
     * de {@link PlatService#getAllPlats}) : un produit du rayon interrogé apparaît, un produit
     * d'un autre rayon de la même boutique n'apparaît pas. Assertion dans les deux sens : une
     * requête cassée qui ne renverrait jamais rien satisferait à tort une simple absence.
     */
    @Test
    @Transactional
    void catalogueBoutiqueEstNavigableParRayonEtInvisibleHorsRayon() {
        Restaurant boutique = creerBoutique();
        Plat produitEpicerie = creerProduit(boutique, "epicerie", 5);
        Plat produitCosmetique = creerProduit(boutique, "cosmetique", 5);

        var page = platService.getAllPlats(null, null, "epicerie", null, null, "ALIMENTAIRE",
                PageRequest.of(0, 500));

        assertThat(page.getContent()).extracting("id")
                .contains(produitEpicerie.getId())
                .doesNotContain(produitCosmetique.getId());
    }

    /**
     * Le cœur de la démonstration : une commande boutique est créée via le {@link CommandeService}
     * générique — le même bean, le même {@code createCommande}, qu'utilise le parcours restaurant,
     * aucune surcharge ni endpoint boutique n'existe. Elle démarre dans le même statut initial
     * ({@code EN_ATTENTE}) et décrémente le stock du produit ; son annulation via
     * {@code cancelCommande} restitue ce stock. Les deux relectures de {@code quantiteStock} sont
     * faites après {@code flush()}/{@code clear()} pour forcer un SELECT frais depuis la base —
     * pas la simple relecture de l'instance Hibernate déjà en mémoire que {@code save()} renvoie.
     */
    @Test
    @Transactional
    void commandeEnBoutiquePasseParLeMemeServiceDecrementeLeStockEtRestitueALAnnulation() {
        Restaurant boutique = creerBoutique();
        Plat produit = creerProduit(boutique, "epicerie", 5);
        User client = creerClient();

        CommandeDTO commande = commandeService.createCommande(
                commandeDto(client.getId(), boutique.getId(), produit.getId(), 2));

        assertThat(commande.getId()).isNotNull();
        // Statut initial identique à celui d'une commande restaurant : aucune machine à états
        // dédiée aux boutiques.
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_ATTENTE.name());
        assertThat(commande.getRestaurant()).isNotNull();
        assertThat(commande.getRestaurant().getId()).isEqualTo(boutique.getId());
        // NB : CommandeServiceImpl#convertToDTO ne recopie pas Restaurant.vertical sur le
        // RestaurantDTO imbriqué (gap pré-existant, hors périmètre de cette tâche) : on ne peut
        // donc pas vérifier ici la verticale via le DTO retourné, seulement via l'id du restaurant.
        // RETRAIT_SUR_PLACE : pas de frais de livraison, calculés par le code existant non modifié.
        assertThat(commande.getFraisLivraison()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(commande.getMontantTotal()).isEqualByComparingTo(new BigDecimal("240.00"));

        entityManager.flush();
        entityManager.clear();
        assertThat(platRepository.findById(produit.getId()).orElseThrow().getQuantiteStock())
                .as("stock relu en base juste après la commande").isEqualTo(3);
        assertThat(commandeRepository.findById(commande.getId()).orElseThrow().getStockRestitue())
                .as("pas encore restitué avant annulation").isNotEqualTo(Boolean.TRUE);

        CommandeDTO annulee = commandeService.cancelCommande(commande.getId());
        assertThat(annulee.getStatut()).isEqualTo(StatutCommande.ANNULEE.name());

        entityManager.flush();
        entityManager.clear();
        assertThat(platRepository.findById(produit.getId()).orElseThrow().getQuantiteStock())
                .as("stock relu en base après annulation : restitué").isEqualTo(5);
        assertThat(commandeRepository.findById(commande.getId()).orElseThrow().getStockRestitue())
                .isTrue();
    }

    /**
     * Commander plus que le stock disponible est refusé, et ne laisse aucune trace : ni
     * décrément partiel du stock, ni commande persistée.
     */
    @Test
    @Transactional
    void commanderPlusQueLeStockEstRefuseEtNeModifieRienEnBase() {
        Restaurant boutique = creerBoutique();
        Plat produit = creerProduit(boutique, "epicerie", 5);
        User client = creerClient();

        assertThrows(BadRequestException.class, () -> commandeService.createCommande(
                commandeDto(client.getId(), boutique.getId(), produit.getId(), 999)));

        assertThat(platRepository.findById(produit.getId()).orElseThrow().getQuantiteStock())
                .as("stock inchangé après refus").isEqualTo(5);
        assertThat(commandeRepository.findByRestaurantIdOrderByCreatedAtDesc(boutique.getId()))
                .as("aucune commande persistée après refus").isEmpty();
    }
}
