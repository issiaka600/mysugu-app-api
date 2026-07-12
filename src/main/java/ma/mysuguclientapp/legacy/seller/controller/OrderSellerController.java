package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.AssignThirdPartyDeliveryDTO;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.CommandeUpdateStatusDTO;
import ma.mysuguclientapp.dtos.DeliveryChargeDateUpdateDTO;
import ma.mysuguclientapp.dtos.UpdatePaymentStatusDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.dto.ErrorsResponse;
import ma.mysuguclientapp.legacy.seller.mapper.OrderSellerMapper;
import ma.mysuguclientapp.legacy.seller.mapper.OrderStatusMapper;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commandes du shim vendeur (contrat 6valley) : {@code /api/v3/seller/orders/*} réutilise
 * {@link CommandeService} en scopant systématiquement au restaurant du vendeur authentifié via
 * {@link SellerContext#currentRestaurant}. Une {@code Commande} appartenant à un AUTRE
 * restaurant -> 404 (jamais de fuite/mutation cross-restaurant) : c'est la garde de sécurité
 * critique de cette tranche, voir docs/superpowers/specs/2026-07-10-vendor-3d-orders-design.md §5.
 */
@RestController
@RequestMapping("/api/v3/seller/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderSellerController {

    private final SellerContext sellerContext;
    private final CommandeService commandeService;
    private final OrderSellerMapper orderMapper;
    private final OrderStatusMapper statusMapper;

    /** GET orders/list?limit&offset&status : commandes du restaurant du vendeur, forme 6valley paginée. */
    @GetMapping("/list")
    public Map<String, Object> list(@AuthenticationPrincipal String email,
                                     @RequestParam(defaultValue = "10") int limit,
                                     @RequestParam(defaultValue = "0") int offset,
                                     @RequestParam(required = false) String status) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        StatutCommande statut = (status == null || status.isBlank() || "all".equalsIgnoreCase(status))
                ? null
                : statusMapper.fromSixValleyStatus(status);
        int safeLimit = Math.max(limit, 1);
        int page = offset / safeLimit;
        Page<CommandeDTO> commandes = commandeService.getAllCommandes(null, restaurant.getId(), statut,
                PageRequest.of(page, safeLimit));
        return orderMapper.toListEnvelope(commandes, limit, offset);
    }

    /** GET orders/{id} : détail (order-details, liste de lignes) d'une commande du vendeur. */
    @GetMapping("/{id}")
    public List<Map<String, Object>> details(@AuthenticationPrincipal String email, @PathVariable Long id) {
        CommandeDTO commande = ownedOrder(email, id);
        return orderMapper.toOrderDetails(commande);
    }

    /**
     * POST orders/order-detail-status/{id} ({@code _method:put} toléré, ignoré côté serveur) :
     * met à jour le statut natif via le mapping 6valley -> StatutCommande (spec §3). Appartenance
     * TOUJOURS vérifiée avant écriture — une commande d'un AUTRE restaurant -> 404 (jamais de
     * mutation cross-tenant). Statut inconnu -> 400 forme 6valley {@code {errors:[...]}} (spec §3).
     */
    @PostMapping("/order-detail-status/{id}")
    public ResponseEntity<?> updateOrderStatus(@AuthenticationPrincipal String email,
                                                @PathVariable Long id,
                                                @RequestBody(required = false) Map<String, Object> body) {
        ownedOrder(email, id); // 404 si la commande n'appartient pas au vendeur
        Object orderStatus = body != null ? body.get("order_status") : null;
        StatutCommande statut;
        try {
            statut = statusMapper.fromSixValleyStatus(orderStatus != null ? orderStatus.toString() : null);
        } catch (ResponseStatusException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorsResponse.of("order-status-001", e.getReason()));
        }
        CommandeUpdateStatusDTO dto = new CommandeUpdateStatusDTO();
        dto.setStatut(statut.name());
        commandeService.updateCommandeStatus(id, dto);
        return ResponseEntity.ok(Map.of("message", "Statut mis à jour."));
    }

    /**
     * POST orders/assign-delivery-man ({@code _method:put} toléré, {@code order_id,
     * delivery_man_id}) : assigne un livreur interne à la commande via
     * {@link CommandeService#assignLivreur}. Appartenance de la commande TOUJOURS vérifiée avant
     * écriture — une commande d'un AUTRE restaurant -> 404 (jamais de mutation cross-tenant).
     * // GAP: vendor->livreur ownership n'est pas natif (livreurs sont globaux, gérés par
     * l'admin) — l'assignation réutilise directement le service natif, sans restriction
     * supplémentaire côté vendeur (umbrella §4).
     */
    @PostMapping("/assign-delivery-man")
    public Map<String, Object> assignDeliveryMan(@AuthenticationPrincipal String email,
                                                   @RequestBody Map<String, Object> body) {
        Long orderId = toLong(body.get("order_id"));
        Long deliveryManId = toLong(body.get("delivery_man_id"));
        ownedOrder(email, orderId); // 404 si la commande n'appartient pas au vendeur
        commandeService.assignLivreur(orderId, deliveryManId);
        return Map.of("message", "Livreur assigné.");
    }

    /**
     * POST orders/assign-third-party-delivery ({@code delivery_service_name,
     * third_party_delivery_tracking_id, order_id}) : assigne un livreur tiers (hors plateforme)
     * via {@link CommandeService#assignThirdPartyDelivery}. Appartenance TOUJOURS vérifiée avant
     * écriture — une commande d'un AUTRE restaurant -> 404 (jamais de mutation cross-tenant).
     * // GAP: pas de champ natif pour third_party_delivery_tracking_id (spec §4) — accepté et
     * ignoré.
     */
    @PostMapping("/assign-third-party-delivery")
    public Map<String, Object> assignThirdPartyDelivery(@AuthenticationPrincipal String email,
                                                          @RequestBody Map<String, Object> body) {
        Long orderId = toLong(body.get("order_id"));
        ownedOrder(email, orderId); // 404 si la commande n'appartient pas au vendeur
        AssignThirdPartyDeliveryDTO dto = new AssignThirdPartyDeliveryDTO();
        Object serviceName = body.get("delivery_service_name");
        dto.setNom(serviceName != null ? serviceName.toString() : null);
        commandeService.assignThirdPartyDelivery(orderId, dto);
        return Map.of("message", "Livreur tiers assigné.");
    }

    /**
     * POST orders/update-payment-status ({@code order_id, payment_status}) : met à jour le
     * statut de paiement natif via le mapping 6valley -> StatutPaiement (spec §3b). Appartenance
     * TOUJOURS vérifiée avant écriture — une commande d'un AUTRE restaurant -> 404 (jamais de
     * mutation cross-tenant).
     */
    @PostMapping("/update-payment-status")
    public ResponseEntity<?> updatePaymentStatus(@AuthenticationPrincipal String email,
                                                  @RequestBody Map<String, Object> body) {
        Long orderId = toLong(body.get("order_id"));
        ownedOrder(email, orderId); // 404 si la commande n'appartient pas au vendeur
        Object paymentStatus = body.get("payment_status");
        StatutPaiement statutPaiement;
        try {
            statutPaiement = statusMapper.fromSixValleyPayment(paymentStatus != null ? paymentStatus.toString() : null);
        } catch (ResponseStatusException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorsResponse.of("payment-status-001", e.getReason()));
        }
        UpdatePaymentStatusDTO dto = new UpdatePaymentStatusDTO();
        dto.setStatutPaiement(statutPaiement);
        commandeService.updatePaymentStatus(orderId, dto);
        return ResponseEntity.ok(Map.of("message", "Statut de paiement mis à jour."));
    }

    /**
     * POST orders/delivery-charge-date-update ({@code _method:put} toléré, {@code order_id,
     * deliveryman_charge, expected_delivery_date}) : réutilise
     * {@link CommandeService#updateDeliveryChargeAndDate}. Appartenance TOUJOURS vérifiée avant
     * écriture — une commande d'un AUTRE restaurant -> 404 (jamais de mutation cross-tenant).
     * Champs absents/invalides ignorés silencieusement (jamais 500).
     */
    @PostMapping("/delivery-charge-date-update")
    public Map<String, Object> deliveryChargeDateUpdate(@AuthenticationPrincipal String email,
                                                          @RequestBody Map<String, Object> body) {
        Long orderId = toLong(body.get("order_id"));
        ownedOrder(email, orderId); // 404 si la commande n'appartient pas au vendeur

        DeliveryChargeDateUpdateDTO dto = new DeliveryChargeDateUpdateDTO();
        Object charge = body.get("deliveryman_charge");
        if (charge != null) {
            try {
                dto.setFraisLivraison(new BigDecimal(charge.toString().trim()));
            } catch (NumberFormatException ignored) {
                // GAP build-minimal: valeur non numérique -> champ ignoré, jamais 500 (spec §6).
            }
        }
        Object expectedDate = body.get("expected_delivery_date");
        if (expectedDate != null) {
            dto.setDateLivraisonPrevue(parseFlexibleDateTime(expectedDate.toString()));
        }
        commandeService.updateDeliveryChargeAndDate(orderId, dto);
        return Map.of("message", "Frais/date de livraison mis à jour.");
    }

    /**
     * POST orders/order-wise-product-upload (multipart, {@code order_id}, {@code _method:put}
     * toléré, {@code digital_file_after_sell}) : build-minimal — le fichier digital de 6valley
     * n'a pas d'équivalent pour des repas, il est accepté et ignoré. Appartenance TOUJOURS
     * vérifiée avant écriture — une commande d'un AUTRE restaurant -> 404. Jamais 500.
     * // BUILD-MINIMAL: digital-file irrelevant to meals (spec §6).
     */
    @PostMapping(value = "/order-wise-product-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> orderWiseProductUpload(@AuthenticationPrincipal String email,
                                                        @RequestParam("order_id") Long orderId,
                                                        @RequestParam(value = "digital_file_after_sell", required = false) MultipartFile file) {
        ownedOrder(email, orderId); // 404 si la commande n'appartient pas au vendeur
        return Map.of("message", "Fichier reçu.");
    }

    /** Parse tolérant d'une date 6valley ({@code "yyyy-MM-dd HH:mm:ss"} ou {@code "yyyy-MM-dd"}). */
    private LocalDateTime parseFlexibleDateTime(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(trimmed.replace(' ', 'T'));
        } catch (Exception e) {
            try {
                return LocalDate.parse(trimmed).atStartOfDay();
            } catch (Exception e2) {
                // GAP build-minimal: format non reconnu -> champ ignoré, jamais 500 (spec §6).
                return null;
            }
        }
    }

    /**
     * GET orders/{id}/live-tracking : adapte le tracking natif
     * ({@link CommandeService#getCommandeTracking}, alimenté par {@code TrackingLocationStore})
     * vers la forme 6valley {@code {latitude, longitude, ...}}. Appartenance TOUJOURS vérifiée
     * avant lecture — une commande d'un AUTRE restaurant -> 404. Jamais de 500 : si aucune
     * position n'est connue, réponse bénigne avec {@code latitude}/{@code longitude} à
     * {@code null}.
     */
    @GetMapping("/{id}/live-tracking")
    public Map<String, Object> liveTracking(@AuthenticationPrincipal String email, @PathVariable Long id) {
        ownedOrder(email, id); // 404 si la commande n'appartient pas au vendeur

        Map<String, Object> tracking;
        try {
            tracking = commandeService.getCommandeTracking(id);
        } catch (Exception e) {
            // Jamais 500 sur le tracking (spec §6) : réponse bénigne si le natif échoue.
            tracking = Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Object liveGps = tracking != null ? tracking.get("liveGps") : null;
        if (liveGps instanceof Map<?, ?> gps) {
            result.put("latitude", gps.get("latitude"));
            result.put("longitude", gps.get("longitude"));
            result.put("updated_at", gps.get("timestamp"));
        } else {
            // Aucune position connue -> forme bénigne (nulls), jamais 500 (spec §6).
            result.put("latitude", null);
            result.put("longitude", null);
            result.put("updated_at", null);
        }
        result.put("order_id", id);
        return result;
    }

    /**
     * Résout la commande par id et vérifie qu'elle appartient au restaurant du vendeur
     * authentifié. Sinon 404 (jamais de fuite cross-restaurant) — garde réutilisée par tous les
     * endpoints commandes (détail, statut, assignation, paiement, tracking, adresse).
     */
    private CommandeDTO ownedOrder(String email, Long id) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        CommandeDTO commande = commandeService.getCommandeById(id);
        if (commande.getRestaurant() == null || commande.getRestaurant().getId() == null
                || !commande.getRestaurant().getId().equals(restaurant.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande non trouvée");
        }
        return commande;
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id invalide");
        }
    }
}
