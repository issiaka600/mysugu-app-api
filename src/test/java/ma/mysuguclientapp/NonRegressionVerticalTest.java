package ma.mysuguclientapp;

import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.RestaurantRepository;
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
    @Autowired TransactionTemplate tx;

    // Suffixe System.nanoTime() : rejouable à l'infini, jamais de collision avec une donnée
    // résiduelle d'un run précédent interrompu.
    private final String marqueur = "ZzMarqueurNonRegression" + System.nanoTime();
    private Long boutiqueId;
    private Long restoId;

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
    }

    @AfterAll
    void cleanup() {
        tx.executeWithoutResult(s -> {
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
}
