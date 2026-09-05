package ma.mysuguclientapp;

import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.interfaces.PlatService;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NonRegressionVerticalTest {

    @Autowired RestaurantService restaurantService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PlatRepository platRepository;
    @Autowired PlatService platService;
    @Autowired TransactionTemplate tx;

    // Suffixe System.nanoTime() : rejouable à l'infini, jamais de collision avec une donnée
    // résiduelle d'un run précédent interrompu.
    private final String marqueur = "ZzMarqueurNonRegression" + System.nanoTime();
    private Long boutiqueId;
    private Long restoId;
    private Long produitBoutiqueId;
    private Long platRestoId;
    private Long restoHistoriqueId;
    private Long platHistoriqueId;

    // NB : un seul @BeforeAll (plutôt que deux, comme suggéré littéralement par le brief) — l'ordre
    // d'exécution entre plusieurs méthodes @BeforeAll n'est pas garanti par JUnit 5, et setupProduits
    // dépend de boutiqueId/restoId déjà persistés.
    @BeforeAll
    void setup() {
        Restaurant boutique = new Restaurant();
        boutique.setNom(marqueur + " Epicerie");
        boutique.setIsActive(true);
        boutique.setVertical(Vertical.ALIMENTAIRE);
        boutique.setAppreciation(5.0);
        boutiqueId = restaurantRepository.save(boutique).getId();

        Restaurant resto = new Restaurant();
        resto.setNom(marqueur + " Resto");
        resto.setIsActive(true);
        resto.setVertical(Vertical.RESTAURANT);
        resto.setAppreciation(5.0);
        restoId = restaurantRepository.save(resto).getId();

        // Donnée historique : vertical volontairement NON renseigné (NULL en base), comme les
        // restaurants créés avant l'introduction de la colonne. C'est le cas que la garantie
        // "paramètre absent => que des restaurants" doit couvrir contre une simple égalité SQL
        // (vertical = 'RESTAURANT' ne matche jamais NULL).
        Restaurant restoHistorique = new Restaurant();
        restoHistorique.setNom(marqueur + " RestoHistorique");
        restoHistorique.setIsActive(true);
        restoHistorique.setAppreciation(5.0);
        restoHistoriqueId = restaurantRepository.save(restoHistorique).getId();

        var produit = new ma.mysuguclientapp.entities.Plat();
        produit.setNom(marqueur + " Savon");
        produit.setPrix(new java.math.BigDecimal("20.00"));
        produit.setIsAvailable(true);
        produit.setCategorieProduit("epicerie");
        produit.setRestaurant(restaurantRepository.findById(boutiqueId).orElseThrow());
        produitBoutiqueId = platRepository.save(produit).getId();

        var plat = new ma.mysuguclientapp.entities.Plat();
        plat.setNom(marqueur + " Tajine");
        plat.setPrix(new java.math.BigDecimal("80.00"));
        plat.setIsAvailable(true);
        plat.setRestaurant(restaurantRepository.findById(restoId).orElseThrow());
        platRestoId = platRepository.save(plat).getId();

        // Plat rattaché au restaurant historique (vertical NULL) : preuve, côté PlatService cette
        // fois, que la clause JPQL "vertical = RESTAURANT AND p.restaurant.vertical IS NULL" couvre
        // bien ce cas — pas seulement RestaurantService (cf. 4769c6c / 055a848).
        var platHistorique = new ma.mysuguclientapp.entities.Plat();
        platHistorique.setNom(marqueur + " CoucousHistorique");
        platHistorique.setPrix(new java.math.BigDecimal("60.00"));
        platHistorique.setIsAvailable(true);
        platHistorique.setRestaurant(restaurantRepository.findById(restoHistoriqueId).orElseThrow());
        platHistoriqueId = platRepository.save(platHistorique).getId();
    }

    @AfterAll
    void cleanup() {
        tx.executeWithoutResult(s -> {
            platRepository.deleteById(produitBoutiqueId);
            platRepository.deleteById(platRestoId);
            platRepository.deleteById(platHistoriqueId);
            restaurantRepository.deleteById(boutiqueId);
            restaurantRepository.deleteById(restoId);
            restaurantRepository.deleteById(restoHistoriqueId);
        });
    }

    @Test
    void searchSansVerticalNeVoitQueLesRestaurants() {
        var resultats = restaurantService.searchRestaurants(marqueur, null);
        assertThat(resultats).extracting("id").contains(restoId).doesNotContain(boutiqueId);
    }

    @Test
    void searchAvecVerticalAlimentaireNeVoitQueLesBoutiques() {
        var resultats = restaurantService.searchRestaurants(marqueur, "ALIMENTAIRE");
        assertThat(resultats).extracting("id").contains(boutiqueId).doesNotContain(restoId);
    }

    @Test
    void searchAvecAllVoitLesDeux() {
        var resultats = restaurantService.searchRestaurants(marqueur, "ALL");
        assertThat(resultats).extracting("id").contains(boutiqueId, restoId);
    }

    // Limit volontairement non plafonnée (Integer.MAX_VALUE plutôt qu'une valeur fixe comme 200) :
    // la base de test est persistante et accumule des établissements au fil des runs. En
    // PostgreSQL, "ORDER BY appreciation DESC" place les NULL en premier, donc tout établissement
    // actif sans note passerait devant nos fixtures notées 5.0 ; avec une limite fixe, ces
    // fixtures finiraient par sortir de la fenêtre et les assertions de présence ci-dessous
    // échoueraient sans rapport avec une régression du code. Ne pas tronquer élimine ce risque.
    @Test
    void topRatedSansVerticalNeVoitQueLesRestaurants() {
        var resultats = restaurantService.getTopRatedRestaurants(Integer.MAX_VALUE, null);
        assertThat(resultats).extracting("id").doesNotContain(boutiqueId);
    }

    @Test
    void topRatedAvecVerticalAlimentaireNeVoitQueLesBoutiques() {
        var resultats = restaurantService.getTopRatedRestaurants(200, "ALIMENTAIRE");
        assertThat(resultats).extracting("id").contains(boutiqueId).doesNotContain(restoId);
    }

    @Test
    void nearbySansVerticalNeVoitQueLesRestaurants() {
        Restaurant boutique = restaurantRepository.findById(boutiqueId).orElseThrow();
        var loc = new ma.mysuguclientapp.entities.Localisation();
        loc.setLatitude(33.5731);
        loc.setLongitude(-7.5898);
        boutique.setLocalisation(loc);
        restaurantRepository.save(boutique);

        Restaurant resto = restaurantRepository.findById(restoId).orElseThrow();
        var locResto = new ma.mysuguclientapp.entities.Localisation();
        locResto.setLatitude(33.5731);
        locResto.setLongitude(-7.5898);
        resto.setLocalisation(locResto);
        restaurantRepository.save(resto);

        var resultats = restaurantService.getNearbyRestaurants(33.5731, -7.5898, 5.0, null);
        assertThat(resultats).extracting("id").contains(restoId).doesNotContain(boutiqueId);
    }

    @Test
    void verticaleInconnueEstRejetee() {
        Assertions.assertThrows(ma.mysuguclientapp.exceptions.BadRequestException.class,
                () -> restaurantService.searchRestaurants(marqueur, "PHARMACIE"));
    }

    @Test
    void platsSansVerticalNeVoientQueLesPlatsDeRestaurant() {
        var page = platService.getAllPlats(null, null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(page.getContent()).extracting("id")
                .contains(platRestoId).doesNotContain(produitBoutiqueId);
    }

    @Test
    void platsFiltresParRayon() {
        var page = platService.getAllPlats(null, null, "epicerie", null, null, "ALIMENTAIRE",
                org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(page.getContent()).extracting("id").contains(produitBoutiqueId);
    }

    @Test
    void platsRayonInexistantRenvoieVide() {
        var page = platService.getAllPlats(null, null, "rayon_inexistant", null, null, "ALIMENTAIRE",
                org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void searchPlatsSansVerticalNeVoitQueLeRestaurant() {
        assertThat(platService.searchPlats(marqueur, null)).extracting("id")
                .contains(platRestoId).doesNotContain(produitBoutiqueId);
    }

    // ===================== Preuve : les restaurants historiques (vertical NULL) restent visibles =====================
    // Une requête dérivée Spring Data sur "vertical = RESTAURANT" ne matche jamais une ligne NULL en
    // SQL. Ces trois tests prouvent que ce n'est pas le cas ici : le restaurant historique créé dans
    // setup() (vertical non renseigné) doit apparaître dans les résultats sans paramètre "vertical".

    @Test
    void searchSansVerticalVoitAussiLeRestaurantHistoriqueSansVertical() {
        var resultats = restaurantService.searchRestaurants(marqueur, null);
        assertThat(resultats).extracting("id").contains(restoHistoriqueId);
    }

    // Cf. commentaire de topRatedSansVerticalNeVoitQueLesRestaurants ci-dessus : limite non
    // plafonnée pour ne pas dépendre du nombre d'établissements sans note déjà en base de test.
    @Test
    void topRatedSansVerticalVoitAussiLeRestaurantHistoriqueSansVertical() {
        var resultats = restaurantService.getTopRatedRestaurants(Integer.MAX_VALUE, null);
        assertThat(resultats).extracting("id").contains(restoHistoriqueId);
    }

    @Test
    void nearbySansVerticalVoitAussiLeRestaurantHistoriqueSansVertical() {
        Restaurant restoHistorique = restaurantRepository.findById(restoHistoriqueId).orElseThrow();
        var loc = new ma.mysuguclientapp.entities.Localisation();
        loc.setLatitude(33.5731);
        loc.setLongitude(-7.5898);
        restoHistorique.setLocalisation(loc);
        restaurantRepository.save(restoHistorique);

        var resultats = restaurantService.getNearbyRestaurants(33.5731, -7.5898, 5.0, null);
        assertThat(resultats).extracting("id").contains(restoHistoriqueId);
    }

    @Test
    void getAllRestaurantsSansVerticalVoitAussiLeRestaurantHistoriqueSansVertical() {
        var page = restaurantService.getAllRestaurants(null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(page.getContent()).extracting("id")
                .contains(restoHistoriqueId, restoId).doesNotContain(boutiqueId);
    }

    // Même preuve que ci-dessus, côté PlatService : un plat rattaché à un établissement historique
    // (vertical jamais renseigné) doit être PRÉSENT dans le listing sans paramètre "vertical". Une
    // assertion de présence, pas d'absence : une requête cassée qui ne renverrait rien satisferait
    // à tort une assertion d'absence.

    @Test
    void platsSansVerticalVoitAussiLePlatDuRestaurantHistoriqueSansVertical() {
        var page = platService.getAllPlats(null, null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(page.getContent()).extracting("id").contains(platHistoriqueId);
    }

    @Test
    void searchPlatsSansVerticalVoitAussiLePlatDuRestaurantHistoriqueSansVertical() {
        assertThat(platService.searchPlats(marqueur, null)).extracting("id")
                .contains(platHistoriqueId);
    }
}
