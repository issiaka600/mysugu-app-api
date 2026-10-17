package ma.mysuguclientapp.services;

import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.ZoneDeploiement;
import ma.mysuguclientapp.services.location.DeliveryPricingService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class DeliveryPricingServiceTest {
    final DeliveryPricingService pricing = new DeliveryPricingService(null);
    Restaurant restaurant() {
        Restaurant r = new Restaurant(); var z = new ZoneDeploiement();
        z.setFraisLivraisonMin(new BigDecimal("15")); r.setZoneDeploiement(z); return r;
    }
    @Test void perKmTariffKeepsFloorRoundingAndPrecedence() {
        var r = restaurant(); var z = r.getZoneDeploiement();
        z.setPrixParKm(new BigDecimal("4")); z.setDistanceMinKm(BigDecimal.TEN); z.setPrixExtraParKm(BigDecimal.ONE);
        assertThat(pricing.price(r, new BigDecimal("2")).fraisLivraison()).isEqualByComparingTo("15");
        var result = pricing.price(r, new BigDecimal("5.251"));
        assertThat(result.fraisLivraison()).isEqualByComparingTo("21.00");
        assertThat(result.tarifBase().add(result.fraisDistance())).isEqualByComparingTo(result.fraisLivraison());
        assertThat(result.distanceKm()).isEqualByComparingTo("5.251");
    }
    @Test void thresholdTariffKeepsMinimumAndCeiling() {
        var r = restaurant(); var z = r.getZoneDeploiement();
        z.setFraisLivraisonMin(new BigDecimal("15.50"));
        z.setDistanceMinKm(new BigDecimal("3")); z.setPrixExtraParKm(new BigDecimal("2"));
        assertThat(pricing.price(r, new BigDecimal("3")).fraisLivraison()).isEqualByComparingTo("15.50");
        assertThat(pricing.price(r, new BigDecimal("3.251")).fraisLivraison()).isEqualByComparingTo("17");
    }
    @Test void globalTariffRetainsOptionalZoneFloor() {
        var r = new Restaurant();
        assertThat(pricing.price(r, new BigDecimal("5.251")).fraisLivraison()).isEqualByComparingTo("21.00");
        var z = new ZoneDeploiement(); z.setFraisLivraisonMin(new BigDecimal("40")); r.setZoneDeploiement(z);
        var result = pricing.price(r, new BigDecimal("5.251"));
        assertThat(result.fraisLivraison()).isEqualByComparingTo("40");
        assertThat(result.fraisDistance()).isEqualByComparingTo("0");
    }
    @Test void baseTariffIsAFloorNotAnAddOn() {
        var r = new Restaurant(); var z = new ZoneDeploiement();
        z.setFraisLivraisonMin(new BigDecimal("5"));
        z.setPrixParKm(new BigDecimal("2"));
        r.setZoneDeploiement(z);

        // 1,5 km x 2 = 3 DH < base 5 -> la base seule s'applique
        var low = pricing.price(r, new BigDecimal("1.5"));
        assertThat(low.fraisLivraison()).isEqualByComparingTo("5");
        assertThat(low.tarifBase()).isEqualByComparingTo("5");
        assertThat(low.fraisDistance()).isEqualByComparingTo("0");

        // 5,2 km x 2 = 10,40 DH > base 5 -> la distance seule, sans ajouter la base
        var high = pricing.price(r, new BigDecimal("5.2"));
        assertThat(high.fraisLivraison()).isEqualByComparingTo("10.40");
        assertThat(high.tarifBase()).isEqualByComparingTo("0");
        assertThat(high.fraisDistance()).isEqualByComparingTo("10.40");
        assertThat(high.tarifBase().add(high.fraisDistance())).isEqualByComparingTo(high.fraisLivraison());
    }
}
