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
    }

    @AfterAll
    void cleanup() {
        tx.executeWithoutResult(s -> {
            platRepository.deleteById(produitBoutiqueId);
            platRepository.deleteById(platRestoId);
            restaurantRepository.deleteById(boutiqueId);
            restaurantRepository.deleteById(restoId);
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

    @Test
    void topRatedSansVerticalNeVoitQueLesRestaurants() {
        var resultats = restaurantService.getTopRatedRestaurants(200, null);
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
        var page = platService.getAllPlats(null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(page.getContent()).extracting("id")
                .contains(platRestoId).doesNotContain(produitBoutiqueId);
    }

    @Test
    void platsFiltresParRayon() {
        var page = platService.getAllPlats(null, null, "epicerie", null, "ALIMENTAIRE",
                org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(page.getContent()).extracting("id").contains(produitBoutiqueId);
    }

    @Test
    void platsRayonInexistantRenvoieVide() {
        var page = platService.getAllPlats(null, null, "rayon_inexistant", null, "ALIMENTAIRE",
                org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void searchPlatsSansVerticalNeVoitQueLeRestaurant() {
        assertThat(platService.searchPlats(marqueur, null)).extracting("id")
                .contains(platRestoId).doesNotContain(produitBoutiqueId);
    }
}
