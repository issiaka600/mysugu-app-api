package ma.mysuguclientapp.services.tracking;

import lombok.NonNull;
import ma.mysuguclientapp.dtos.tracking.GpsLocationDTO;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrackingLocationStore {

    private final Map<Long, GpsLocationDTO> latestLocations = new ConcurrentHashMap<>();

    public void save(@NonNull GpsLocationDTO location) {
        Long commandeId = location.getCommandeId();
        if (commandeId == null) {
            return;
        }
        latestLocations.put(commandeId, location);
    }

    public GpsLocationDTO getLatest(@NonNull Long commandeId) {
        return latestLocations.get(commandeId);
    }
}
