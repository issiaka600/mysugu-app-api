package ma.mysuguclientapp;

import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RayonsBoutiqueTest {

    @Autowired RestaurantService restaurantService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PlatRepository platRepository;
    @Autowired TransactionTemplate tx;

    private Long boutiqueId;
    private Long restoId;

    @BeforeAll
    void setup() {
        Restaurant boutique = new Restaurant();
        boutique.setNom("Superette " + System.nanoTime());
        boutique.setIsActive(true);
        boutique.setVertical(Vertical.ALIMENTAIRE);
        boutique = restaurantRepository.save(boutique);
        boutiqueId = boutique.getId();

        platRepository.save(produit(boutique, "Pommes", "fruits_legumes"));
        platRepository.save(produit(boutique, "Riz", "epicerie"));
        platRepository.save(produit(boutique, "Bananes", "fruits_legumes"));

        Restaurant resto = new Restaurant();
        resto.setNom("Resto " + System.nanoTime());
        resto.setIsActive(true);
        resto.setVertical(Vertical.RESTAURANT);
        resto = restaurantRepository.save(resto);
        restoId = resto.getId();
        platRepository.save(produit(resto, "Tajine", null));
    }

    private Plat produit(Restaurant commerce, String nom, String rayon) {
        Plat p = new Plat();
        p.setNom(nom);
        p.setPrix(new BigDecimal("15.00"));
        p.setIsAvailable(true);
        p.setRestaurant(commerce);
        p.setCategorieProduit(rayon);
        return p;
    }

    @AfterAll
    void cleanup() {
        tx.executeWithoutResult(s -> {
            platRepository.deleteAll(platRepository.findByRestaurantId(boutiqueId));
            platRepository.deleteAll(platRepository.findByRestaurantId(restoId));
            restaurantRepository.deleteById(boutiqueId);
            restaurantRepository.deleteById(restoId);
        });
    }

    @Test
    void laBoutiqueExposeSesRayonsNonVidesAvecLeurLibelle() {
        var rayons = restaurantService.getRestaurantById(boutiqueId).getRayons();
        assertThat(rayons).extracting("value").containsExactly("fruits_legumes", "epicerie");
        assertThat(rayons).extracting("label").containsExactly("Fruits & légumes", "Épicerie");
    }

    @Test
    void unRestaurantNExposeAucunRayon() {
        assertThat(restaurantService.getRestaurantById(restoId).getRayons()).isEmpty();
    }
}
