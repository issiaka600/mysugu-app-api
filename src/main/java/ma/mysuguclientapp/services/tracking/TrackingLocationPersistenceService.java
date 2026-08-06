package ma.mysuguclientapp.services.tracking;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.tracking.GpsLocationDTO;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.HistoriqueGpsLivraison;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.HistoriqueGpsLivraisonRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Persiste les positions WebSocket, alimente la position fraîche du livreur et le cache temps réel. */
@Service
@RequiredArgsConstructor
public class TrackingLocationPersistenceService {

    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final HistoriqueGpsLivraisonRepository gpsRepository;
    private final TrackingLocationStore trackingLocationStore;

    @Transactional
    public GpsLocationDTO save(GpsLocationDTO location) {
        if (location == null || location.getCommandeId() == null || location.getLivreurId() == null
                || location.getLatitude() == null || location.getLongitude() == null) {
            throw new IllegalArgumentException("commandeId, livreurId, latitude et longitude sont requis");
        }

        Commande commande = commandeRepository.findById(location.getCommandeId())
                .orElseThrow(() -> new IllegalArgumentException("Commande introuvable"));
        User livreur = userRepository.findById(location.getLivreurId())
                .orElseThrow(() -> new IllegalArgumentException("Livreur introuvable"));
        if (commande.getLivreur() == null || !commande.getLivreur().getId().equals(livreur.getId())) {
            throw new IllegalArgumentException("Le livreur n'est pas assigné à cette commande");
        }

        LocalDateTime timestamp = location.getTimestamp() != null ? location.getTimestamp() : LocalDateTime.now();
        location.setTimestamp(timestamp);
        gpsRepository.save(HistoriqueGpsLivraison.builder()
                .commande(commande)
                .livreur(livreur)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .vitesse(location.getVitesse())
                .localisation(location.getStatut())
                .pointAt(timestamp)
                .build());

        Localisation position = livreur.getLocalisation() != null ? livreur.getLocalisation() : new Localisation();
        position.setLatitude(location.getLatitude());
        position.setLongitude(location.getLongitude());
        livreur.setLocalisation(position);
        livreur.setLastLocationAt(timestamp);
        userRepository.save(livreur);

        trackingLocationStore.save(location);
        return location;
    }
}
