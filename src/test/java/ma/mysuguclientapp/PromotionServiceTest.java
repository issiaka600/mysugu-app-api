package ma.mysuguclientapp;

import ma.mysuguclientapp.dtos.commerce.PromotionCreateDTO;
import ma.mysuguclientapp.dtos.commerce.PromotionDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.repositories.PromotionRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.interfaces.PromotionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration pour PromotionService.
 *
 * Valide les deux bugs corrigés dans creerPromotion() :
 *  1. Bug ORM : Promotion est le côté inverse du @OneToOne — le lien doit passer par Restaurant.setPromotion()
 *  2. Bug NPE : Une promo flash sans restaurantId (restaurantId = null) ne doit pas planter
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PromotionServiceTest {

    @Autowired
    PromotionService promotionService;

    @Autowired
    PromotionRepository promotionRepository;

    @Autowired
    RestaurantRepository restaurantRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    /** IDs créés par les tests — nettoyés en @AfterAll */
    private Long promotionFlashId;
    private Long promotionAvecRestaurantId;
    private Long restaurantTestId;

    @BeforeAll
    void setupRestaurant() {
        Restaurant r = new Restaurant();
        r.setNom("Restaurant Test Promotion");
        r.setIsActive(true);
        restaurantTestId = restaurantRepository.save(r).getId();
    }

    @AfterAll
    void cleanup() {
        transactionTemplate.executeWithoutResult(s -> {
            // Dissocier la promotion du restaurant avant de supprimer (FK contrainte)
            restaurantRepository.findById(restaurantTestId).ifPresent(restaurant -> {
                restaurant.setPromotion(null);
                restaurantRepository.save(restaurant);
            });
            if (promotionFlashId != null) promotionRepository.deleteById(promotionFlashId);
            if (promotionAvecRestaurantId != null) promotionRepository.deleteById(promotionAvecRestaurantId);
            restaurantRepository.deleteById(restaurantTestId);
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 1 : Promo flash SANS restaurant (restaurantId = null)
    // Avant le fix : plantait avec IllegalArgumentException("The given id must not be null")
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    void creerPromotionFlash_sansRestaurant_doitReussir() {
        PromotionCreateDTO dto = new PromotionCreateDTO();
        dto.setDescription("Flash 50% plateforme");
        dto.setPourcentage(50);
        dto.setEstFlash(true);
        dto.setDateDebut(LocalDateTime.now());
        dto.setDateFin(LocalDateTime.now().plusHours(2));
        dto.setCode("FLASH50");
        dto.setMontantMinCommande(BigDecimal.valueOf(100));
        dto.setUsageMax(200);
        // restaurantId volontairement null

        PromotionDTO result = promotionService.creerPromotion(dto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getEstFlash()).isTrue();
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getPourcentage()).isEqualTo(50);
        assertThat(result.getCode()).isEqualTo("FLASH50");
        assertThat(result.getRestaurantId()).isNull(); // pas de restaurant
        assertThat(result.getRestaurantNom()).isNull();

        promotionFlashId = result.getId();
        System.out.println("[TEST 1 OK] Promo flash créée sans restaurant, id=" + promotionFlashId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 2 : Promo liée à un restaurant — la FK doit être persistée en BDD
    // Avant le fix : Hibernate ignorait le .restaurant() du builder (côté inverse)
    //               → Restaurant.promotion_id restait NULL en base
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @Order(2)
    void creerPromotion_avecRestaurant_doitPersisterFK() {
        PromotionCreateDTO dto = new PromotionCreateDTO();
        dto.setDescription("Promo restaurant -20%");
        dto.setPourcentage(20);
        dto.setEstFlash(false);
        dto.setDateDebut(LocalDateTime.now());
        dto.setDateFin(LocalDateTime.now().plusDays(7));
        dto.setCode("REST20");
        dto.setMontantMinCommande(BigDecimal.valueOf(50));
        dto.setUsageMax(100);
        dto.setRestaurantId(restaurantTestId);

        PromotionDTO result = promotionService.creerPromotion(dto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getRestaurantId()).isEqualTo(restaurantTestId);
        assertThat(result.getRestaurantNom()).isEqualTo("Restaurant Test Promotion");
        assertThat(result.getPourcentage()).isEqualTo(20);

        promotionAvecRestaurantId = result.getId();

        // Vérification directe en base : la FK promotion_id doit être settée sur le restaurant
        Restaurant restaurantEnBase = restaurantRepository.findById(restaurantTestId).orElseThrow();
        assertThat(restaurantEnBase.getPromotion()).isNotNull();
        assertThat(restaurantEnBase.getPromotion().getId()).isEqualTo(promotionAvecRestaurantId);

        System.out.println("[TEST 2 OK] Promo liée au restaurant, FK promotion_id=" + promotionAvecRestaurantId
                + " bien persistée sur le restaurant id=" + restaurantTestId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 3 : getPromotionsFlash() doit retourner la promo flash active
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @Order(3)
    void getPromotionsFlash_doitRetournerPromoFlashActive() {
        var flashPromos = promotionService.getPromotionsFlash();
        assertThat(flashPromos).isNotEmpty();
        assertThat(flashPromos.stream().anyMatch(p -> p.getId().equals(promotionFlashId))).isTrue();
        System.out.println("[TEST 3 OK] getPromotionsFlash() retourne " + flashPromos.size() + " promo(s) flash");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 4 : getPromotionsRestaurant() doit retourner la promo du restaurant
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @Order(4)
    void getPromotionsRestaurant_doitRetournerPromoLiee() {
        var promos = promotionService.getPromotionsRestaurant(restaurantTestId);
        assertThat(promos).isNotEmpty();
        assertThat(promos.stream().anyMatch(p -> p.getId().equals(promotionAvecRestaurantId))).isTrue();
        System.out.println("[TEST 4 OK] getPromotionsRestaurant(" + restaurantTestId + ") retourne " + promos.size() + " promo(s)");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 5 : activerDesactiver() — désactivation et réactivation
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @Order(5)
    void activerDesactiver_doitChangerEtat() {
        // Désactiver
        PromotionDTO desactivee = promotionService.activerDesactiver(promotionFlashId, false);
        assertThat(desactivee.getIsActive()).isFalse();

        // Réactiver
        PromotionDTO reactivee = promotionService.activerDesactiver(promotionFlashId, true);
        assertThat(reactivee.getIsActive()).isTrue();

        System.out.println("[TEST 5 OK] activerDesactiver() fonctionne correctement");
    }
}
