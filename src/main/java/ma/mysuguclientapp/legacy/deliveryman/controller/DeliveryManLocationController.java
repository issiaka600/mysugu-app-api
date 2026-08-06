package ma.mysuguclientapp.legacy.deliveryman.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.HistoriqueGpsLivraison;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.deliveryman.dto.MessageResponse;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.HistoriqueGpsLivraisonRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Localisation / GPS du livreur (contrat 6valley §5.1–§5.5).
 * distance-api : structure Google Distance Matrix synthétisée (Haversine) — pas de dépendance
 * clé Google/réseau. seller-location : depuis le restaurant de la commande. GPS persisté en base.
 */
@RestController
@RequestMapping("/api/v2/delivery-man")
@RequiredArgsConstructor
public class DeliveryManLocationController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final CommandeRepository commandeRepository;
    private final HistoriqueGpsLivraisonRepository gpsRepository;

    @PostMapping("/record-location-data")
    public ResponseEntity<?> recordLocation(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        Long orderId = asLong(body.get("order_id"));
        Commande c = orderId != null ? commandeRepository.findById(orderId).orElse(null) : null;
        if (c == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("errors", List.of(Map.of("code", "order", "message", "Commande introuvable."))));
        }
        if (c.getLivreur() == null || !c.getLivreur().getId().equals(l.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("errors", List.of(Map.of("code", "order", "message", "Commande non assignée au livreur."))));
        }
        LocalDateTime now = LocalDateTime.now();
        gpsRepository.save(HistoriqueGpsLivraison.builder()
                .commande(c).livreur(l)
                .latitude(asDouble(body.get("latitude")))
                .longitude(asDouble(body.get("longitude")))
                .vitesse(asDouble(body.get("speed")))
                .localisation(str(body.get("location")))
                .pointAt(now)
                .build());
        Localisation position = l.getLocalisation() != null ? l.getLocalisation() : new Localisation();
        position.setLatitude(asDouble(body.get("latitude")));
        position.setLongitude(asDouble(body.get("longitude")));
        l.setLocalisation(position);
        l.setLastLocationAt(now);
        userRepository.save(l);
        return ResponseEntity.ok(new MessageResponse("location recorded"));
    }

    @GetMapping("/last-location")
    public Object lastLocation(@AuthenticationPrincipal String email, @RequestParam("order_id") Long orderId) {
        requireAssignedOrder(orderId, livreur(email));
        return gpsRepository.findFirstByCommandeIdOrderByPointAtDesc(orderId)
                .map(this::toHistoryMap).orElse(null);
    }

    @GetMapping("/order-delivery-history")
    public List<Map<String, Object>> deliveryHistory(@AuthenticationPrincipal String email, @RequestParam("order_id") Long orderId) {
        requireAssignedOrder(orderId, livreur(email));
        return gpsRepository.findByCommandeIdOrderByPointAtAsc(orderId).stream().map(this::toHistoryMap).toList();
    }

    @PostMapping("/distance-api")
    public Map<String, Object> distance(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User livreur = livreur(email);
        double oLat = asDouble(body.get("origin_lat")), oLng = asDouble(body.get("origin_lng"));
        double dLat = asDouble(body.get("destination_lat")), dLng = asDouble(body.get("destination_lng"));
        double meters = haversine(oLat, oLng, dLat, dLng);
        long seconds = Math.round(meters / 8.33); // ~30 km/h

        Map<String, Object> distance = new LinkedHashMap<>();
        distance.put("text", String.format(Locale.US, "%.1f km", meters / 1000.0));
        distance.put("value", meters);
        Map<String, Object> duration = new LinkedHashMap<>();
        duration.put("text", (seconds / 60) + " mins");
        duration.put("value", (double) seconds);
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("distance", distance);
        element.put("duration", duration);
        element.put("status", "OK");
        Map<String, Object> row = Map.of("elements", List.of(element));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("destination_addresses", List.of(""));
        out.put("origin_addresses", List.of(""));
        out.put("rows", List.of(row));
        out.put("status", "OK");
        return out;
    }

    @GetMapping("/seller-location")
    public Map<String, Object> sellerLocation(@AuthenticationPrincipal String email,
                                              @RequestParam(name = "order_id", required = false) Long orderId,
                                              @RequestParam(name = "seller_id", required = false) Long sellerId) {
        User livreur = livreur(email);
        Double lat = null, lng = null;
        if (orderId != null) {
            Commande c = requireAssignedOrder(orderId, livreur);
            if (c.getRestaurant() != null && c.getRestaurant().getLocalisation() != null) {
                lat = c.getRestaurant().getLocalisation().getLatitude();
                lng = c.getRestaurant().getLocalisation().getLongitude();
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("latitude", lat != null ? lat.toString() : "0");
        m.put("longitude", lng != null ? lng.toString() : "0");
        return m;
    }

    private Map<String, Object> toHistoryMap(HistoriqueGpsLivraison h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId());
        m.put("order_id", h.getCommande() != null ? h.getCommande().getId() : null);
        m.put("deliveryman_id", h.getLivreur() != null ? h.getLivreur().getId() : null);
        m.put("latitude", h.getLatitude() != null ? h.getLatitude().toString() : "0");
        m.put("longitude", h.getLongitude() != null ? h.getLongitude().toString() : "0");
        m.put("location", h.getLocalisation());
        m.put("time", h.getPointAt() != null ? h.getPointAt().format(TS) : null);
        m.put("created_at", h.getCreatedAt() != null ? h.getCreatedAt().format(TS) : null);
        m.put("updated_at", h.getCreatedAt() != null ? h.getCreatedAt().format(TS) : null);
        return m;
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static Long asLong(Object o) {
        try { return o == null ? null : Long.valueOf(o.toString()); } catch (Exception e) { return null; }
    }

    private static double asDouble(Object o) {
        try { return o == null ? 0 : Double.parseDouble(o.toString()); } catch (Exception e) { return 0; }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private User livreur(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Livreur non authentifié"));
        if (u.getRole() != UserRole.LIVREUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux livreurs");
        }
        return u;
    }

    private Commande requireAssignedOrder(Long orderId, User livreur) {
        Commande commande = commandeRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande introuvable"));
        if (commande.getLivreur() == null || !commande.getLivreur().getId().equals(livreur.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cette commande n'est pas assignée au livreur");
        }
        return commande;
    }
}
