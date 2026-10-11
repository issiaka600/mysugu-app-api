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
        assertThat(result.fraisLivraison()).isEqualByComparingTo("22");
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
        assertThat(pricing.price(r, new BigDecimal("5.251")).fraisLivraison()).isEqualByComparingTo("32");
        var z = new ZoneDeploiement(); z.setFraisLivraisonMin(new BigDecimal("40")); r.setZoneDeploiement(z);
        var result = pricing.price(r, new BigDecimal("5.251"));
        assertThat(result.fraisLivraison()).isEqualByComparingTo("40");
        assertThat(result.fraisDistance()).isEqualByComparingTo("0");
    }
}
