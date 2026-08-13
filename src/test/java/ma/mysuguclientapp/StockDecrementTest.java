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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
     * (options différentes, même plat). Le décrément doit être cumulatif. CommandeServiceImpl
     * agrège les quantités par {@code platId} avant la boucle de construction des lignes et
     * n'appelle {@code StockService.reserver} qu'une seule fois par produit distinct, avec la
     * quantité totale déjà sommée (2 + 3 = 5 unités réservées en un seul appel, pas deux appels
     * de 2 puis 3) — le cumul est donc garanti par l'agrégation en amont, pas par un enchaînement
     * de deux verrous successifs sur le même plat.
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

    /** Nombre de répétitions des tests de course : sans mise en course forcée, un seul essai
     * peut passer par chance de timing (T1 committe avant que T2 ne démarre) sans jamais avoir
     * exercé le chevauchement des deux transactions. Répéter augmente la probabilité que les
     * deux threads soient effectivement en vol en même temps sur au moins une itération. */
    private static final int ITERATIONS_COURSE = 8;

    /**
     * Piège n°1 du brief : deux commandes concurrentes portant les deux mêmes produits mais
     * soumis dans un ordre différent ne doivent jamais s'interbloquer. Ce test lance deux
     * vraies transactions en parallèle (deux threads, deux connexions) — contrairement aux
     * tests ci-dessus, il n'est PAS @Transactional : le verrouillage pessimiste n'a de sens
     * qu'entre transactions réellement distinctes. Le nettoyage est donc fait manuellement.
     *
     * Un {@link CountDownLatch} force les deux threads à démarrer leur appel à createCommande()
     * au même instant (plutôt que deux pool.submit() consécutifs, où rien ne garantit que les
     * transactions se chevauchent réellement) ; répété {@link #ITERATIONS_COURSE} fois pour
     * maximiser la probabilité d'un chevauchement effectif sur au moins une itération.
     */
    @Test
    void createCommandeSimultaneesOrdreInverseNeSeBloquentPas() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < ITERATIONS_COURSE; i++) {
                Restaurant boutique = creerBoutique();
                Plat platA = creerProduitEnStock(50, boutique);
                Plat platB = creerProduitEnStock(50, boutique);
                User clientA = creerClient();
                User clientB = creerClient();

                Future<CommandeDTO> f1 = null;
                Future<CommandeDTO> f2 = null;
                try {
                    CountDownLatch depart = new CountDownLatch(1);
                    f1 = pool.submit(() -> {
                        depart.await();
                        return commandeService.createCommande(commandeAvecLignes(
                                clientA.getId(), boutique.getId(), platA.getId(), platB.getId()));
                    });
                    f2 = pool.submit(() -> {
                        depart.await();
                        return commandeService.createCommande(commandeAvecLignes(
                                clientB.getId(), boutique.getId(), platB.getId(), platA.getId()));
                    });
                    depart.countDown();

                    // Si le tri par platId avant verrouillage n'était pas fait, ces deux commandes
                    // verrouilleraient A puis B et B puis A respectivement : interblocage garanti.
                    CommandeDTO c1 = f1.get(20, TimeUnit.SECONDS);
                    CommandeDTO c2 = f2.get(20, TimeUnit.SECONDS);

                    assertThat(c1).as("iteration %d", i).isNotNull();
                    assertThat(c2).as("iteration %d", i).isNotNull();

                    // Chaque commande prend 1 unité de A et 1 de B : les deux décréments doivent
                    // s'être appliqués (50 - 2 = 48), preuve qu'aucune des deux transactions n'a été
                    // perdue ni bloquée indéfiniment.
                    assertThat(platRepository.findById(platA.getId()).orElseThrow().getQuantiteStock())
                            .as("iteration %d : stock A", i).isEqualTo(48);
                    assertThat(platRepository.findById(platB.getId()).orElseThrow().getQuantiteStock())
                            .as("iteration %d : stock B", i).isEqualTo(48);
                } finally {
                    // Si un get(20s) a timeout — précisément le scénario d'interblocage que ce
                    // test doit attraper — l'autre thread peut être encore en vol avec sa
                    // transaction ouverte : annuler les deux Future avant de toucher aux données
                    // évite qu'un deleteById ne se bloque à son tour sur le verrou encore tenu, ce
                    // qui masquerait l'échec d'origine derrière une erreur secondaire.
                    if (f1 != null) f1.cancel(true);
                    if (f2 != null) f2.cancel(true);
                    nettoyerCommandesEtBoutique(boutique.getId());
                    platRepository.deleteById(platA.getId());
                    platRepository.deleteById(platB.getId());
                    restaurantRepository.deleteById(boutique.getId());
                    // Les clients ne sont volontairement PAS supprimés : notifierPartiesCommande()
                    // leur a créé des Notification, elles-mêmes référencées par
                    // tentatives_notification_fcm — remonter toute cette chaîne FK dans un test
                    // n'apporte rien. Emails uniques par nanoTime() : ne compromet pas la
                    // rejouabilité (juste un peu de garbage inoffensif dans la base de test).
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Propriété métier centrale de la tâche, celle que le test précédent ne démontre pas :
     * sur un stock unitaire, deux acheteurs simultanés ne peuvent pas tous les deux repartir
     * avec le produit. Exactement une des deux commandes réussit, l'autre échoue avec
     * {@link BadRequestException} ("stock insuffisant"), et le stock final est 0 — jamais -1,
     * jamais encore 1. Même dispositif de mise en course que le test précédent (CountDownLatch
     * + répétitions) pour ne pas dépendre d'un hasard de timing.
     */
    @Test
    void deuxAcheteursSimultanesSurStockUnitaireUnSeulReussit() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < ITERATIONS_COURSE; i++) {
                Restaurant boutique = creerBoutique();
                Plat produit = creerProduitEnStock(1, boutique);
                User clientA = creerClient();
                User clientB = creerClient();

                Future<CommandeDTO> f1 = null;
                Future<CommandeDTO> f2 = null;
                try {
                    CountDownLatch depart = new CountDownLatch(1);
                    f1 = pool.submit(() -> {
                        depart.await();
                        return commandeService.createCommande(
                                commandeUneLigne(clientA.getId(), boutique.getId(), produit.getId()));
                    });
                    f2 = pool.submit(() -> {
                        depart.await();
                        return commandeService.createCommande(
                                commandeUneLigne(clientB.getId(), boutique.getId(), produit.getId()));
                    });
                    depart.countDown();

                    int succes = 0;
                    int refus = 0;
                    for (Future<CommandeDTO> f : List.of(f1, f2)) {
                        try {
                            CommandeDTO c = f.get(20, TimeUnit.SECONDS);
                            assertThat(c).as("iteration %d", i).isNotNull();
                            succes++;
                        } catch (ExecutionException e) {
                            assertThat(e.getCause()).as("iteration %d : cause du refus", i)
                                    .isInstanceOf(BadRequestException.class);
                            refus++;
                        }
                    }

                    assertThat(succes).as("iteration %d : exactement un succes", i).isEqualTo(1);
                    assertThat(refus).as("iteration %d : exactement un refus", i).isEqualTo(1);
                    assertThat(platRepository.findById(produit.getId()).orElseThrow().getQuantiteStock())
                            .as("iteration %d : stock final", i).isEqualTo(0);
                } finally {
                    // Cf. commentaire équivalent dans le test précédent : annuler les Future avant
                    // de toucher aux données évite qu'un timeout (le scénario même que ce test
                    // doit attraper) ne soit masqué par un deleteById bloqué sur un verrou encore
                    // tenu par un thread toujours en vol.
                    if (f1 != null) f1.cancel(true);
                    if (f2 != null) f2.cancel(true);
                    nettoyerCommandesEtBoutique(boutique.getId());
                    platRepository.deleteById(produit.getId());
                    restaurantRepository.deleteById(boutique.getId());
                }
            }
        } finally {
            pool.shutdownNow();
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

    private CommandeCreateDTO commandeUneLigne(Long clientId, Long restaurantId, Long platId) {
        CommandeCreateDTO dto = new CommandeCreateDTO();
        dto.setClientId(clientId);
        dto.setRestaurantId(restaurantId);
        dto.setMethodePaiement("ESPECES");
        dto.setModeReception("RETRAIT_SUR_PLACE");

        LigneCommandeCreateDTO l = new LigneCommandeCreateDTO();
        l.setPlatId(platId);
        l.setQuantite(1);
        dto.setLignes(List.of(l));
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
