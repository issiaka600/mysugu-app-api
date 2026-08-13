package ma.mysuguclientapp;

import ma.mysuguclientapp.dtos.CommandeCreateDTO;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.LigneCommandeCreateDTO;
import ma.mysuguclientapp.dtos.cart.AjouterItemDTO;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.repositories.AlerteCommandeVendeurRepository;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.PanierServiceImpl;
import ma.mysuguclientapp.services.implementations.StockService;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class StockDecrementTest {

    private Plat plat(Boolean disponible, Integer stock) {
        Plat p = new Plat();
        p.setNom("Produit");
        p.setIsAvailable(disponible);
        p.setQuantiteStock(stock);
        return p;
    }

    @Test
    void platSansStockGereSuitLeFlagVendeur() {
        assertThat(plat(true, null).isEffectivementDisponible()).isTrue();
        assertThat(plat(false, null).isEffectivementDisponible()).isFalse();
    }

    @Test
    void stockEpuiseRendIndisponibleMemeSiFlagActif() {
        assertThat(plat(true, 0).isEffectivementDisponible()).isFalse();
    }

    @Test
    void stockPositifEtFlagActifDonneDisponible() {
        assertThat(plat(true, 3).isEffectivementDisponible()).isTrue();
    }

    @Test
    void flagVendeurDesactiveGagneSurLeStock() {
        assertThat(plat(false, 100).isEffectivementDisponible()).isFalse();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Tests d'intégration : StockService, CommandeServiceImpl, PanierServiceImpl
    // ────────────────────────────────────────────────────────────────────────

    @Autowired
    PlatRepository platRepository;
    @Autowired
    RestaurantRepository restaurantRepository;
    @Autowired
    StockService stockService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    CommandeService commandeService;
    @Autowired
    CommandeRepository commandeRepository;
    @Autowired
    AlerteCommandeVendeurRepository alerteCommandeVendeurRepository;
    @Autowired
    PanierServiceImpl panierService;

    private Restaurant creerBoutique() {
        Restaurant boutique = new Restaurant();
        boutique.setNom("Boutique stock " + System.nanoTime());
        boutique.setIsActive(true);
        boutique.setVertical(Vertical.ALIMENTAIRE);
        return restaurantRepository.save(boutique);
    }

    private Plat creerProduitEnStock(int stock) {
        Restaurant boutique = creerBoutique();

        Plat produit = new Plat();
        produit.setNom("Riz 5kg");
        produit.setPrix(new BigDecimal("50.00"));
        produit.setRestaurant(boutique);
        produit.setIsAvailable(true);
        produit.setQuantiteStock(stock);
        return platRepository.save(produit);
    }

    private Plat creerProduitEnStock(int stock, Restaurant boutique) {
        Plat produit = new Plat();
        produit.setNom("Produit " + System.nanoTime());
        produit.setPrix(new BigDecimal("50.00"));
        produit.setRestaurant(boutique);
        produit.setIsAvailable(true);
        produit.setQuantiteStock(stock);
        return platRepository.save(produit);
    }

    private User creerClient() {
        User client = new User();
        client.setEmail("stock-test-" + System.nanoTime() + "@test.mysugu");
        client.setPassword("password-hash");
        client.setNom("Client");
        client.setPrenom("Test");
        client.setRole(UserRole.CLIENT);
        client.setIsActive(true);
        return userRepository.save(client);
    }

    @Test
    @Transactional
    void reserverDecrementeLeStock() {
        Plat produit = creerProduitEnStock(10);
        stockService.reserver(produit.getId(), 3);
        assertThat(platRepository.findById(produit.getId()).orElseThrow().getQuantiteStock()).isEqualTo(7);
    }

    @Test
    @Transactional
    void reserverRefuseSiStockInsuffisant() {
        Plat produit = creerProduitEnStock(2);
        assertThrows(BadRequestException.class, () -> stockService.reserver(produit.getId(), 5));
        assertThat(platRepository.findById(produit.getId()).orElseThrow().getQuantiteStock()).isEqualTo(2);
    }

    @Test
    @Transactional
    void reserverIgnoreLesPlatsSansStockGere() {
        Plat plat = creerProduitEnStock(5);
        plat.setQuantiteStock(null);
        platRepository.save(plat);
        stockService.reserver(plat.getId(), 999);
        assertThat(platRepository.findById(plat.getId()).orElseThrow().getQuantiteStock()).isNull();
    }

    /**
     * Propagation.MANDATORY est délibéré : appeler reserver() hors de toute transaction
     * doit exploser immédiatement plutôt que de démarrer silencieusement une transaction
     * isolée qui masquerait le bug. Ce test n'est PAS annoté @Transactional : c'est
     * précisément le but, prouver que l'appel échoue en l'absence de transaction ambiante.
     */
    @Test
    void reserverHorsTransactionLeveUneExceptionExplicite() {
        Plat produit = creerProduitEnStock(5);
        try {
            assertThrows(
                    org.springframework.transaction.IllegalTransactionStateException.class,
                    () -> stockService.reserver(produit.getId(), 1));
            assertThat(platRepository.findById(produit.getId()).orElseThrow().getQuantiteStock()).isEqualTo(5);
        } finally {
            Long boutiqueId = produit.getRestaurant().getId();
            platRepository.deleteById(produit.getId());
            restaurantRepository.deleteById(boutiqueId);
        }
    }

    /**
     * Piège n°2 du brief : le même produit apparaît sur deux lignes de la même commande
     * (options différentes, même plat). Le décrément doit être cumulatif dans la même
     * transaction — l'instance managée par le premier appel à StockService.reserver()
     * porte déjà la valeur décrémentée au second appel, grâce à l'identity map Hibernate
     * (même EntityManager, même transaction).
     */
    @Test
    @Transactional
    void createCommandeCumuleLeDecrementPourLeMemeProduitSurDeuxLignes() {
        Restaurant boutique = creerBoutique();
        Plat produit = creerProduitEnStock(10, boutique);
        User client = creerClient();

        CommandeCreateDTO dto = new CommandeCreateDTO();
        dto.setClientId(client.getId());
        dto.setRestaurantId(boutique.getId());
        dto.setMethodePaiement("ESPECES");
        dto.setModeReception("RETRAIT_SUR_PLACE");

        LigneCommandeCreateDTO ligne1 = new LigneCommandeCreateDTO();
        ligne1.setPlatId(produit.getId());
        ligne1.setQuantite(2);
        LigneCommandeCreateDTO ligne2 = new LigneCommandeCreateDTO();
        ligne2.setPlatId(produit.getId());
        ligne2.setQuantite(3);
        dto.setLignes(List.of(ligne1, ligne2));

        CommandeDTO commande = commandeService.createCommande(dto);
        assertThat(commande).isNotNull();

        // 10 - 2 - 3 = 5 : les deux lignes ont bien été décomptées cumulativement,
        // pas seulement la dernière écrite.
        assertThat(platRepository.findById(produit.getId()).orElseThrow().getQuantiteStock()).isEqualTo(5);
    }

    @Test
    @Transactional
    void createCommandeRefuseSiCumulDesLignesDepasseLeStock() {
        Restaurant boutique = creerBoutique();
        Plat produit = creerProduitEnStock(4, boutique);
        User client = creerClient();

        CommandeCreateDTO dto = new CommandeCreateDTO();
        dto.setClientId(client.getId());
        dto.setRestaurantId(boutique.getId());
        dto.setMethodePaiement("ESPECES");
        dto.setModeReception("RETRAIT_SUR_PLACE");

        LigneCommandeCreateDTO ligne1 = new LigneCommandeCreateDTO();
        ligne1.setPlatId(produit.getId());
        ligne1.setQuantite(3);
        LigneCommandeCreateDTO ligne2 = new LigneCommandeCreateDTO();
        ligne2.setPlatId(produit.getId());
        ligne2.setQuantite(3); // 3 + 3 = 6 > 4 en stock
        dto.setLignes(List.of(ligne1, ligne2));

        assertThrows(BadRequestException.class, () -> commandeService.createCommande(dto));
        // L'échec survient dans la boucle de construction des lignes, avant l'appel à
        // commandeRepository.save(commande) : aucune commande ne doit avoir été persistée.
        assertThat(commandeRepository.findByRestaurantIdOrderByCreatedAtDesc(boutique.getId())).isEmpty();
    }

    @Test
    @Transactional
    void ajouterAuPanierRefuseUnProduitEnRupture() {
        Plat produit = creerProduitEnStock(0); // isAvailable=true mais quantiteStock=0
        User client = creerClient();

        AjouterItemDTO dto = new AjouterItemDTO();
        dto.setPlatId(produit.getId());
        dto.setQuantite(1);

        assertThrows(IllegalArgumentException.class, () -> panierService.ajouterItem(client.getId(), dto));
    }

    @Test
    @Transactional
    void ajouterAuPanierAccepteUnProduitEnStock() {
        Plat produit = creerProduitEnStock(3);
        User client = creerClient();

        AjouterItemDTO dto = new AjouterItemDTO();
        dto.setPlatId(produit.getId());
        dto.setQuantite(1);

        var result = panierService.ajouterItem(client.getId(), dto);
        assertThat(result).isNotNull();
    }

    /**
     * Piège n°1 du brief : deux commandes concurrentes portant les deux mêmes produits mais
     * soumis dans un ordre différent ne doivent jamais s'interbloquer. Ce test lance deux
     * vraies transactions en parallèle (deux threads, deux connexions) — contrairement aux
     * tests ci-dessus, il n'est PAS @Transactional : le verrouillage pessimiste n'a de sens
     * qu'entre transactions réellement distinctes. Le nettoyage est donc fait manuellement.
     */
    @Test
    void createCommandeSimultaneesOrdreInverseNeSeBloquentPas() throws Exception {
        Restaurant boutique = creerBoutique();
        Plat platA = creerProduitEnStock(50, boutique);
        Plat platB = creerProduitEnStock(50, boutique);
        User clientA = creerClient();
        User clientB = creerClient();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<CommandeDTO> f1 = pool.submit(() ->
                    commandeService.createCommande(commandeAvecLignes(
                            clientA.getId(), boutique.getId(), platA.getId(), platB.getId())));
            Future<CommandeDTO> f2 = pool.submit(() ->
                    commandeService.createCommande(commandeAvecLignes(
                            clientB.getId(), boutique.getId(), platB.getId(), platA.getId())));

            // Si le tri par platId avant verrouillage n'était pas fait, ces deux commandes
            // verrouilleraient A puis B et B puis A respectivement : interblocage garanti.
            CommandeDTO c1 = f1.get(20, TimeUnit.SECONDS);
            CommandeDTO c2 = f2.get(20, TimeUnit.SECONDS);

            assertThat(c1).isNotNull();
            assertThat(c2).isNotNull();

            // Chaque commande prend 1 unité de A et 1 de B : les deux décréments doivent
            // s'être appliqués (50 - 2 = 48), preuve qu'aucune des deux transactions n'a été
            // perdue ni bloquée indéfiniment.
            assertThat(platRepository.findById(platA.getId()).orElseThrow().getQuantiteStock()).isEqualTo(48);
            assertThat(platRepository.findById(platB.getId()).orElseThrow().getQuantiteStock()).isEqualTo(48);
        } finally {
            pool.shutdownNow();
            nettoyerCommandesEtBoutique(boutique.getId());
            platRepository.deleteById(platA.getId());
            platRepository.deleteById(platB.getId());
            restaurantRepository.deleteById(boutique.getId());
            // Les clients ne sont volontairement PAS supprimés : notifierPartiesCommande() leur a
            // créé des Notification, elles-mêmes référencées par tentatives_notification_fcm —
            // remonter toute cette chaîne FK dans un test n'apporte rien. Les emails étant
            // uniques par nanoTime(), laisser ces users en base ne compromet pas la rejouabilité
            // du test (juste un peu de garbage inoffensif dans la base de test persistante).
        }
    }

    private CommandeCreateDTO commandeAvecLignes(Long clientId, Long restaurantId, Long platId1, Long platId2) {
        CommandeCreateDTO dto = new CommandeCreateDTO();
        dto.setClientId(clientId);
        dto.setRestaurantId(restaurantId);
        dto.setMethodePaiement("ESPECES");
        dto.setModeReception("RETRAIT_SUR_PLACE");

        LigneCommandeCreateDTO l1 = new LigneCommandeCreateDTO();
        l1.setPlatId(platId1);
        l1.setQuantite(1);
        LigneCommandeCreateDTO l2 = new LigneCommandeCreateDTO();
        l2.setPlatId(platId2);
        l2.setQuantite(1);
        dto.setLignes(List.of(l1, l2));
        return dto;
    }

    private void nettoyerCommandesEtBoutique(Long restaurantId) {
        commandeRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId).forEach(c -> {
            alerteCommandeVendeurRepository.findByCommandeId(c.getId())
                    .ifPresent(alerteCommandeVendeurRepository::delete);
            commandeRepository.deleteById(c.getId());
        });
    }
}
