package ma.mysuguclientapp.services.location;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.DetailFraisDTO;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.util.Constants;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class DeliveryPricingService {
    private final GoogleMapsLocationService maps;

    public DetailFraisDTO calculate(Restaurant restaurant, Localisation destination) {
        var origin = restaurant.getLocalisation();
        BigDecimal distance = maps.routeKm(origin.getLatitude(), origin.getLongitude(),
                destination.getLatitude(), destination.getLongitude());
        return price(restaurant, distance);
    }

    /** Keep the current zone tariff precedence, floors and rounding, using road kilometres. */
    public DetailFraisDTO price(Restaurant restaurant, BigDecimal distance) {
        var zone = restaurant.getZoneDeploiement();
        BigDecimal base, total;
        if (zone != null && zone.getPrixParKm() != null && zone.getFraisLivraisonMin() != null) {
            base = zone.getFraisLivraisonMin();
            total = distance.multiply(zone.getPrixParKm()).max(base).setScale(0, RoundingMode.UP);
        } else if (zone != null && zone.getFraisLivraisonMin() != null
                && zone.getDistanceMinKm() != null && zone.getPrixExtraParKm() != null) {
            base = zone.getFraisLivraisonMin();
            total = distance.compareTo(zone.getDistanceMinKm()) <= 0 ? base
                    : base.add(distance.subtract(zone.getDistanceMinKm()).multiply(zone.getPrixExtraParKm()))
                            .setScale(0, RoundingMode.UP);
        } else {
            base = BigDecimal.valueOf(Constants.BASE_DELIVERY_FEE_MAD);
            total = base.add(distance.multiply(BigDecimal.valueOf(Constants.DELIVERY_FEE_PER_KM_MAD)))
                    .setScale(0, RoundingMode.UP);
            if (zone != null && zone.getFraisLivraisonMin() != null) {
                base = base.max(zone.getFraisLivraisonMin());
                total = total.max(zone.getFraisLivraisonMin());
            }
        }
        return new DetailFraisDTO(distance, base, total.subtract(base), total);
    }
}
