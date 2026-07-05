package ma.mysuguclientapp.legacy.deliveryman.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.deliveryman.mapper.LegacyOrderMapper;
import ma.mysuguclientapp.legacy.deliveryman.service.DeliveryManOrderService;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Commandes du livreur (contrat 6valley §3). current/all/details/search + accept FCFS.
 * current-orders inclut aussi les commandes NON assignées revendiquables (modèle FCFS, techspec §8).
 */
@RestController
@RequestMapping("/api/v2/delivery-man")
@RequiredArgsConstructor
public class DeliveryManOrderController {

    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final LegacyOrderMapper mapper;
    private final DeliveryManOrderService orderService;

    /** Commandes en cours du livreur + commandes libres revendiquables (bare array). */
    @GetMapping("/current-orders")
    public List<Map<String, Object>> currentOrders(@AuthenticationPrincipal String email) {
        User l = livreur(email);
        List<Commande> mine = commandeRepository.findByLivreurIdAndStatutInOrderByCreatedAtDesc(
                l.getId(), DeliveryManOrderService.ACTIFS);
        List<Commande> libres = commandeRepository.findByStatutInAndLivreurIsNullOrderByCreatedAtAsc(
                DeliveryManOrderService.REVENDIQUABLES);
        List<Map<String, Object>> out = new ArrayList<>();
        mine.forEach(c -> out.add(mapper.toOrderMap(c, false)));
        libres.forEach(c -> out.add(mapper.toOrderMap(c, false)));
        return out;
    }

    /** Historique complet du livreur, filtré (status/search/date/is_pause). Bare array. */
    @GetMapping("/all-orders")
    public List<Map<String, Object>> allOrders(@AuthenticationPrincipal String email,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(name = "is_pause", required = false) String isPause) {
        User l = livreur(email);
        return commandeRepository.findByLivreurIdOrderByCreatedAtDesc(l.getId()).stream()
                .filter(c -> status == null || status.isBlank()
                        || LegacyOrderMapper.toLegacyStatus(c.getStatut()).equalsIgnoreCase(status))
                .filter(c -> isPause == null || isPause.isBlank()
                        || ("1".equals(isPause)) == Boolean.TRUE.equals(c.getEnPause()))
                .filter(c -> search == null || search.isBlank() || matchesSearch(c, search))
                .map(c -> mapper.toOrderMap(c, false))
                .collect(Collectors.toList());
    }

    @GetMapping("/order-list-by-date")
    public List<Map<String, Object>> orderListByDate(@AuthenticationPrincipal String email,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(name = "is_pause", required = false) String isPause) {
        return allOrders(email, status, null, isPause);
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@AuthenticationPrincipal String email,
                                           @RequestParam(required = false) String search) {
        User l = livreur(email);
        return commandeRepository.findByLivreurIdOrderByCreatedAtDesc(l.getId()).stream()
                .filter(c -> search == null || search.isBlank() || matchesSearch(c, search))
                .map(c -> mapper.toOrderMap(c, false))
                .collect(Collectors.toList());
    }

    @GetMapping("/order-details")
    public ResponseEntity<?> orderDetails(@AuthenticationPrincipal String email,
                                          @RequestParam("order_id") Long orderId) {
        User l = livreur(email);
        Commande c = commandeRepository.findById(orderId).orElse(null);
        if (c == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Commande introuvable."));
        }
        if (c.getLivreur() != null && !c.getLivreur().getId().equals(l.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Cette commande est assignée à un autre livreur."));
        }
        Map<String, Object> order = mapper.toOrderMap(c, true);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("order", order);
        body.put("details", order.get("details"));
        body.put("customer", order.get("customer"));
        body.put("shipping_address", order.get("shipping_address"));
        return ResponseEntity.ok(body);
    }

    /** POST /{orderId}/accept — revendication FCFS. Réponse {message, order}. */
    @PostMapping("/{orderId}/accept")
    public ResponseEntity<?> accept(@AuthenticationPrincipal String email, @PathVariable Long orderId) {
        User l = livreur(email);
        Map<String, Object> order = orderService.accept(orderId, l);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Commande acceptée avec succès.");
        body.put("order", order);
        return ResponseEntity.ok(body);
    }

    private boolean matchesSearch(Commande c, String search) {
        String s = search.toLowerCase();
        if (c.getNumeroCommande() != null && c.getNumeroCommande().toLowerCase().contains(s)) return true;
        if (c.getId() != null && c.getId().toString().equals(search)) return true;
        if (c.getClient() != null) {
            String name = ((c.getClient().getPrenom() != null ? c.getClient().getPrenom() : "") + " "
                    + (c.getClient().getNom() != null ? c.getClient().getNom() : "")).toLowerCase();
            return name.contains(s);
        }
        return false;
    }

    private User livreur(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Livreur non authentifié"));
        if (u.getRole() != UserRole.LIVREUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux livreurs");
        }
        return u;
    }
}
