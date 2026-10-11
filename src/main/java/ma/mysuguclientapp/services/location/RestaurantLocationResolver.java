package ma.mysuguclientapp.services.location;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.RestaurantCreateDTO;
import ma.mysuguclientapp.exceptions.LocationContractException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RestaurantLocationResolver {
    private final GoogleMapsLocationService maps;

    /** Validate/resolve the whole location before any restaurant or media write. */
    public void resolve(RestaurantCreateDTO update, Boolean confirmation) {
        var loc = update.getLocalisation();
        if (loc == null && confirmation == null) return;
        if (loc == null || loc.getAdresse() == null || loc.getAdresse().isBlank())
            throw LocationContractException.required();
        if (loc.getLatitude() != null || loc.getLongitude() != null) {
            if (!Boolean.TRUE.equals(confirmation)
                    || !GoogleMapsLocationService.validCoordinates(loc.getLatitude(), loc.getLongitude()))
                throw LocationContractException.required();
        } else {
            var point = maps.geocode(loc.getAdresse());
            loc.setLatitude(point.latitude());
            loc.setLongitude(point.longitude());
        }
        loc.setAdresse(loc.getAdresse().trim());
        update.setLocationConfirmed(true);
    }
}
