package ma.mysuguclientapp.services.tracking;

import lombok.NonNull;
import ma.mysuguclientapp.dtos.tracking.GpsLocationDTO;
import ma.mysuguclientapp.repositories.HistoriqueGpsLivraisonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class TrackingLocationStore {

    private final Map<Long, GpsLocationDTO> latestLocations = new ConcurrentHashMap<>();
    private final HistoriqueGpsLivraisonRepository gpsRepository;

    public void save(@NonNull GpsLocationDTO location) {
        Long commandeId = location.getCommandeId();
        if (commandeId == null) {
            return;
        }
        latestLocations.put(commandeId, location);
    }

    public GpsLocationDTO getLatest(@NonNull Long commandeId) {
        GpsLocationDTO cached = latestLocations.get(commandeId);
        if (cached != null) return cached;

        return gpsRepository.findFirstByCommandeIdOrderByPointAtDesc(commandeId)
                .map(point -> {
                    GpsLocationDTO restored = GpsLocationDTO.builder()
                            .commandeId(commandeId)
                            .livreurId(point.getLivreur() != null ? point.getLivreur().getId() : null)
                            .latitude(point.getLatitude())
                            .longitude(point.getLongitude())
                            .vitesse(point.getVitesse())
                            .statut(point.getLocalisation())
                            .timestamp(point.getPointAt())
                            .build();
                    latestLocations.put(commandeId, restored);
                    return restored;
                }).orElse(null);
    }
}
