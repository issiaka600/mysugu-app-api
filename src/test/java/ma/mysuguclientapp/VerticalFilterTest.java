package ma.mysuguclientapp;

import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VerticalFilterTest {

    @Autowired RestaurantService restaurantService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired TransactionTemplate tx;

    private Long alimId;
    private Long restoId;

    @BeforeAll
    void setup() {
        Restaurant alim = new Restaurant();
        alim.setNom("Epicerie Test " + System.nanoTime());
        alim.setIsActive(true);
        alim.setVertical(Vertical.ALIMENTAIRE);
        alimId = restaurantRepository.save(alim).getId();

        Restaurant resto = new Restaurant();
        resto.setNom("Resto Test " + System.nanoTime());
        resto.setIsActive(true);
        resto.setVertical(Vertical.RESTAURANT);
        restoId = restaurantRepository.save(resto).getId();
    }

    @AfterAll
    void cleanup() {
        tx.executeWithoutResult(s -> {
            restaurantRepository.deleteById(alimId);
            restaurantRepository.deleteById(restoId);
        });
    }

    @Test
    void filtreParVerticalAlimentaire() {
        var page = restaurantService.getAllRestaurants(null, null, null, null, "ALIMENTAIRE", PageRequest.of(0, 100));
        assertThat(page.getContent()).anyMatch(r -> r.getId().equals(alimId));
        assertThat(page.getContent()).noneMatch(r -> r.getId().equals(restoId));
        assertThat(page.getContent()).allMatch(r -> "ALIMENTAIRE".equals(r.getVertical()));
    }

    @Test
    void sansParamRenvoieRestaurantSeulement() {
        var page = restaurantService.getAllRestaurants(null, null, null, null, null, PageRequest.of(0, 100));
        assertThat(page.getContent()).noneMatch(r -> r.getId().equals(alimId));
    }

    @Test
    void allRenvoieTousLesVerticaux() {
        var page = restaurantService.getAllRestaurants(null, null, null, null, "ALL", PageRequest.of(0, 100));
        assertThat(page.getContent()).anyMatch(r -> r.getId().equals(alimId));
        assertThat(page.getContent()).anyMatch(r -> r.getId().equals(restoId));
    }
}
