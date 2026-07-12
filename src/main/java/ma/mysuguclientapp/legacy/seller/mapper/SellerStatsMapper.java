package ma.mysuguclientapp.legacy.seller.mapper;

import ma.mysuguclientapp.dtos.restaurant.RestaurantDashboardDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduit {@link RestaurantDashboardDTO} natif (buckets today/week/month/total) -> les
 * contrats 6valley "order-statistics" et "get-earning-statitics" consommés par le tableau de
 * bord de Tiktak-vendor-app-moso, derrière {@code /api/v3/seller/*}. Pure traduction de forme
 * — aucune donnée n'est chargée ici (voir {@code SellerStatsController}).
 * Voir docs/superpowers/specs/2026-07-10-vendor-3j-stats-notifications-design.md §3.1/§3.2 et
 * la correction 3j.0 (clés réelles confirmées contre le code Dart de l'app vendeur).
 */
@Component
public class SellerStatsMapper {

    /**
     * GET order-statistics?statistics_type= -> objet compteurs 6valley. Clés confirmées via
     * {@code lib/features/order/domain/models/business_analytics_filter_data.dart}
     * (BusinessAnalyticsFilterDataModel) : pending/confirmed/processing/out_for_delivery/
     * delivered/canceled/returned/failed (3j.0). {@code total} ajouté en bonus (spec §3.1),
     * ignoré par l'app mais inoffensif. Le natif n'a pas de ventilation par statut et par
     * période (uniquement un compteur "aujourd'hui" livré/annulé) : delivered/canceled
     * restent toujours les compteurs du jour, quel que soit le bucket sélectionné pour
     * {@code total} — approximation documentée (spec §3.1, §5).
     */
    public Map<String, Object> orderStatistics(RestaurantDashboardDTO dto, String statisticsType) {
        String type = normalizeOrderType(statisticsType);
        long total = switch (type) {
            case "this_week" -> nz(dto.getCommandesSemaine());
            case "this_month" -> nz(dto.getCommandesMois());
            case "overall" -> nz(dto.getTotalCommandes());
            default -> nz(dto.getCommandesAujourdhui());
        };

        long pending = nz(dto.getCommandesEnCours());
        long processing = nz(dto.getCommandesEnPreparation());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", pending);
        m.put("confirmed", processing);
        m.put("processing", processing);
        m.put("out_for_delivery", pending);
        m.put("delivered", nz(dto.getCommandesLivreesAujourdhui()));
        m.put("canceled", nz(dto.getCommandesAnnuleesAujourdhui()));
        m.put("returned", 0);
        m.put("failed", 0);
        m.put("total", total);
        return m;
    }

    private String normalizeOrderType(String statisticsType) {
        if (statisticsType == null || statisticsType.isBlank()) {
            return "today";
        }
        return switch (statisticsType.trim().toLowerCase()) {
            case "this_week", "week" -> "this_week";
            case "this_month", "month" -> "this_month";
            case "overall", "all", "total" -> "overall";
            default -> "today";
        };
    }

    private static long nz(Long v) {
        return v != null ? v : 0L;
    }
}
