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

    /**
     * Le tarif de base est un <b>plancher</b> : il ne s'applique que si le coût
     * {@code distance × prixParKm} est inférieur. Au-delà du seuil, on facture
     * uniquement la distance, sans y rajouter le tarif de base.
     *
     * <p>Exemple : base 5 DH, 2 DH/km ⇒ 3 DH de distance → 5 DH ;
     * 5,2 km ⇒ 10,40 DH (et non 5 + 10,40).</p>
     */
    public DetailFraisDTO price(Restaurant restaurant, BigDecimal distance) {
        var zone = restaurant.getZoneDeploiement();
        BigDecimal base, total;
        if (zone != null && zone.getPrixParKm() != null && zone.getFraisLivraisonMin() != null) {
            BigDecimal minimum = zone.getFraisLivraisonMin();
            BigDecimal distanceFee = distance.multiply(zone.getPrixParKm())
                    .setScale(2, RoundingMode.HALF_UP);
            if (distanceFee.compareTo(minimum) <= 0) {
                base = minimum;
                total = minimum;
            } else {
                base = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                total = distanceFee;
            }
        } else if (zone != null && zone.getFraisLivraisonMin() != null
                && zone.getDistanceMinKm() != null && zone.getPrixExtraParKm() != null) {
            // Grille historique "forfait jusqu'à X km puis supplément" : inchangée.
            base = zone.getFraisLivraisonMin();
            total = distance.compareTo(zone.getDistanceMinKm()) <= 0 ? base
                    : base.add(distance.subtract(zone.getDistanceMinKm()).multiply(zone.getPrixExtraParKm()))
                            .setScale(0, RoundingMode.UP);
        } else {
            BigDecimal minimum = BigDecimal.valueOf(Constants.BASE_DELIVERY_FEE_MAD);
            if (zone != null && zone.getFraisLivraisonMin() != null) {
                minimum = minimum.max(zone.getFraisLivraisonMin());
            }
            BigDecimal distanceFee = distance
                    .multiply(BigDecimal.valueOf(Constants.DELIVERY_FEE_PER_KM_MAD))
                    .setScale(2, RoundingMode.HALF_UP);
            if (distanceFee.compareTo(minimum) <= 0) {
                base = minimum;
                total = minimum;
            } else {
                base = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                total = distanceFee;
            }
        }
        return new DetailFraisDTO(distance, base, total.subtract(base), total);
    }
}
