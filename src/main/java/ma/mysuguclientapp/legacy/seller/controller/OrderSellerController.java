package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.OrderSellerMapper;
import ma.mysuguclientapp.legacy.seller.mapper.OrderStatusMapper;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
}
