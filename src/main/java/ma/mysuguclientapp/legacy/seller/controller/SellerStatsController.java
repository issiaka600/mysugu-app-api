package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.restaurant.RestaurantDashboardDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.SellerStatsMapper;
import ma.mysuguclientapp.services.implementations.RestaurantDashboardServiceImpl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Statistiques du tableau de bord du shim vendeur (contrat 6valley) :
 * {@code /api/v3/seller/order-statistics}, {@code /api/v3/seller/get-earning-statitics}
 * (orthographe 6valley conservée telle quelle) et {@code /api/v3/seller/top-delivery-man}.
 * Réutilise {@link RestaurantDashboardServiceImpl#getDashboard(Long)} scopé au restaurant du
 * vendeur authentifié via {@link SellerContext#currentRestaurant}. Aucune requête directe aux
 * repositories de commandes — toute la donnée transite par le service natif.
 * Voir docs/superpowers/specs/2026-07-10-vendor-3j-stats-notifications-design.md.
 */
@RestController
@RequestMapping("/api/v3/seller")
@RequiredArgsConstructor
public class SellerStatsController {

    private final SellerContext sellerContext;
    private final RestaurantDashboardServiceImpl dashboardService;
    private final SellerStatsMapper mapper;

    /**
     * GET order-statistics?statistics_type= : compteurs de commandes du restaurant du vendeur
     * authentifié, sélection de bucket selon {@code statistics_type} (spec §3.1). Type
     * absent/inconnu -> "today" (jamais 500).
     */
    @GetMapping("/order-statistics")
    public Map<String, Object> orderStatistics(@AuthenticationPrincipal String email,
                                               @RequestParam(value = "statistics_type", required = false) String statisticsType) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        RestaurantDashboardDTO dto = dashboardService.getDashboard(restaurant.getId());
        return mapper.orderStatistics(dto, statisticsType);
    }

    /**
     * GET get-earning-statitics?type= (orthographe 6valley volontairement conservée) : série de
     * gains synthétisée à partir des buckets CA du restaurant du vendeur authentifié (spec §3.2,
     * approximation documentée dans {@link SellerStatsMapper#earningStatistics}). Type
     * absent/inconnu -> "today" (jamais 500).
     */
    @GetMapping("/get-earning-statitics")
    public Map<String, Object> earningStatistics(@AuthenticationPrincipal String email,
                                                 @RequestParam(value = "type", required = false) String type) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        RestaurantDashboardDTO dto = dashboardService.getDashboard(restaurant.getId());
        return mapper.earningStatistics(dto, type, restaurant.getCommissionPourcentage());
    }

    /**
     * GET top-delivery-man : STUB bénin (jamais de fuite du classement admin-global à un seul
     * vendeur) — décision et justification complète dans
     * {@link SellerStatsMapper#topDeliveryManStub}. Toujours 200, jamais 500.
     */
    @GetMapping("/top-delivery-man")
    public Map<String, Object> topDeliveryMan(@AuthenticationPrincipal String email) {
        sellerContext.requireOwner(email);
        return mapper.topDeliveryManStub();
    }
}
