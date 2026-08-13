package ma.mysuguclientapp;

import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VerticalFilterTest {

    @Autowired RestaurantService restaurantService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired TransactionTemplate tx;
    @Autowired ma.mysuguclientapp.repositories.CategoriesRestaurantRepository categorieRepository;
    @Autowired ma.mysuguclientapp.repositories.ServiceCategorieRepository serviceCategorieRepository;
    @Autowired ma.mysuguclientapp.services.interfaces.ServiceCategorieService serviceCategorieService;
    @Autowired ma.mysuguclientapp.services.interfaces.CategorieRestaurantService categorieRestaurantService;

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

    @Test
    @org.springframework.transaction.annotation.Transactional
    void categoriesFiltreesParVerticale() {
        var cosmetique = new ma.mysuguclientapp.entities.CategorieRestaurant();
        cosmetique.setNom("Parfumerie " + System.nanoTime());
        cosmetique.setVertical(Vertical.COSMETIQUE);
        var sauvee = categorieRepository.save(cosmetique);
        assertThat(categorieRepository.findByVerticalEffectif(Vertical.COSMETIQUE))
                .extracting("id").contains(sauvee.getId());
        assertThat(categorieRepository.findByVerticalEffectif(Vertical.RESTAURANT))
                .extracting("id").doesNotContain(sauvee.getId());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void categoriesSansVerticaleSontDesCategoriesRestaurant() {
        var cuisine = new ma.mysuguclientapp.entities.CategorieRestaurant();
        cuisine.setNom("Cuisine test " + System.nanoTime());
        // vertical volontairement null : donnée historique
        var sauvee = categorieRepository.save(cuisine);
        assertThat(categorieRepository.findByVerticalEffectif(Vertical.RESTAURANT))
                .extracting("id").contains(sauvee.getId());
    }

    @Test
    void laTuileDAccueilPorteSaVerticale() {
        var tuile = new ma.mysuguclientapp.entities.ServiceCategorie();
        tuile.setNom("Boutiques test " + System.nanoTime());
        tuile.setVertical(Vertical.ALIMENTAIRE);
        tuile.setIsActive(true);
        var sauvee = serviceCategorieRepository.save(tuile);
        try {
            assertThat(serviceCategorieService.getAllServices())
                    .filteredOn(s -> s.getId().equals(sauvee.getId()))
                    .allMatch(s -> "ALIMENTAIRE".equals(s.getVertical()));
        } finally {
            serviceCategorieRepository.deleteById(sauvee.getId());
        }
    }

    @Test
    void creationTuileAvecVerticalePersisteLaVerticale() {
        var dto = serviceCategorieService.createService(
                "Tuile avec vertical " + System.nanoTime(), null, null, null, null,
                "alimentaire", 0, true, null, null);
        try {
            // Relecture indépendante (hors transaction du create) : preuve d'écriture en base,
            // pas seulement de l'entité en mémoire renvoyée par repository.save().
            var relue = serviceCategorieService.getServiceById(dto.getId());
            assertThat(relue.getVertical()).isEqualTo("ALIMENTAIRE");
        } finally {
            serviceCategorieRepository.deleteById(dto.getId());
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void creationTuileSansVerticaleGardeComportementHistorique() {
        var dto = serviceCategorieService.createService(
                "Tuile sans vertical " + System.nanoTime(), null, null, null, null,
                null, 0, true, null, null);
        assertThat(dto.getVertical()).isNull();
    }

    @Test
    void creationTuileVerticaleInvalideRejetee() {
        assertThatThrownBy(() -> serviceCategorieService.createService(
                "Tuile invalide " + System.nanoTime(), null, null, null, null,
                "PHARMACIE", 0, true, null, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void creationCategorieRestaurantAvecVerticalePersisteLaVerticale() {
        var dto = categorieRestaurantService.createCategorie(
                "Categorie avec vertical " + System.nanoTime(), null, "cosmetique", null, null, null);
        try {
            // Relecture indépendante (hors transaction du create) : preuve d'écriture en base,
            // pas seulement de l'entité en mémoire renvoyée par repository.save().
            var relue = categorieRestaurantService.getCategorieById(dto.getId());
            assertThat(relue.getVertical()).isEqualTo("COSMETIQUE");
        } finally {
            categorieRepository.deleteById(dto.getId());
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void creationCategorieRestaurantSansVerticaleGardeComportementHistorique() {
        var dto = categorieRestaurantService.createCategorie(
                "Categorie sans vertical " + System.nanoTime(), null, null, null, null, null);
        assertThat(dto.getVertical()).isEqualTo("RESTAURANT");
    }

    @Test
    void creationCategorieRestaurantVerticaleInvalideRejetee() {
        assertThatThrownBy(() -> categorieRestaurantService.createCategorie(
                "Categorie invalide " + System.nanoTime(), null, "PHARMACIE", null, null, null))
                .isInstanceOf(BadRequestException.class);
    }
}
