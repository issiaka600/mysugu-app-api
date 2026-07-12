package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.NotificationDTO;
import ma.mysuguclientapp.entities.Notification;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.SellerNotificationMapper;
import ma.mysuguclientapp.repositories.NotificationRepository;
import ma.mysuguclientapp.services.interfaces.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Notifications du shim vendeur (contrat 6valley) : {@code /api/v3/seller/notification*}
 * réutilise {@link NotificationService#getMesNotifications}/{@code marquerCommeLue} en forwardant
 * le header {@code Authorization} entrant (le natif résout le propriétaire depuis le token JWT —
 * spec §4). L'appartenance de la notification consultée est TOUJOURS revérifiée côté shim via
 * {@link #requireOwnedNotification} avant toute mutation : une notification d'un AUTRE vendeur
 * -> 404 (jamais 401/marquage cross-vendeur — garde de sécurité critique de cette tranche,
 * distincte du 401 "Unauthorized" que lève le service natif pour le même cas ; le shim court-
 * circuite volontairement avant d'atteindre marquerCommeLue pour renvoyer 404).
 * Voir docs/superpowers/specs/2026-07-10-vendor-3j-stats-notifications-design.md §3.3/§3.4.
 */
@RestController
@RequestMapping("/api/v3/seller")
@RequiredArgsConstructor
public class SellerNotificationController {

    private final SellerContext sellerContext;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final SellerNotificationMapper mapper;

    /**
     * GET notification?limit=&offset= : notifications du vendeur authentifié, enveloppe de
     * pagination 6valley (spec §3.3). {@code Pageable} dérivé de {@code limit}/{@code offset}
     * (offset = décalage, pas un numéro de page — convention utilisée par le reste du shim,
     * ex. coupon/list, orders/list).
     */
    @GetMapping("/notification")
    public Map<String, Object> list(@AuthenticationPrincipal String email,
                                    @RequestHeader("Authorization") String authorization,
                                    @RequestParam(defaultValue = "10") int limit,
                                    @RequestParam(defaultValue = "0") int offset) {
        sellerContext.requireOwner(email);
        int safeLimit = Math.max(limit, 1);
        int page = offset > 0 ? offset / safeLimit : 0;
        Pageable pageable = PageRequest.of(page, safeLimit, Sort.by("createdAt").descending());
        Page<NotificationDTO> result = notificationService.getMesNotifications(authorization, pageable);
        long unseen = notificationService.getNombreNonLues(authorization);
        return mapper.envelope(result, limit, offset, unseen);
    }

    /**
     * GET notification/view?id= : marque la notification comme lue ({@code lue=true} ;
     * {@code marquerCommeLue}). Appartenance TOUJOURS vérifiée avant écriture — notification
     * d'un AUTRE vendeur (ou id absent/introuvable) -> 404, jamais de marquage cross-vendeur.
     * Réponse bénigne {@code {message}} : l'app (seenNotification) ne lit que le statusCode.
     */
    @GetMapping("/notification/view")
    public Map<String, Object> view(@AuthenticationPrincipal String email,
                                    @RequestHeader("Authorization") String authorization,
                                    @RequestParam(value = "id", required = false) Long id) {
        User owner = sellerContext.requireOwner(email);
        requireOwnedNotification(id, owner); // 404 si absente/introuvable/pas au vendeur
        notificationService.marquerCommeLue(authorization, id);
        return mapper.success("Notification marquée comme lue.");
    }

    /**
     * Charge la notification par id et vérifie qu'elle appartient au vendeur authentifié. Sinon
     * 404 (jamais de fuite/marquage cross-vendeur) — court-circuite AVANT marquerCommeLue pour
     * que le contrat shim reste 404 (le natif lève un 401 "Unauthorized" pour ce même cas, ce
     * qui ne correspond pas au contrat attendu ici).
     */
    private void requireOwnedNotification(Long id, User owner) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification non trouvée");
        }
        Notification notif = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification non trouvée"));
        if (notif.getDestinataire() == null || !notif.getDestinataire().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification non trouvée");
        }
    }
}
