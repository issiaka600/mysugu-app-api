package ma.mysuguclientapp.legacy.seller.mapper;

import ma.mysuguclientapp.dtos.restaurant.RestaurantDashboardDTO;
import ma.mysuguclientapp.enumerations.StatutCommande;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
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
    /**
     * Compteurs de commandes 6valley à partir des comptes RÉELS par statut du restaurant
     * (une entrée {@link StatutCommande} -> nombre). Mappe chaque statut natif vers le bucket
     * 6valley EXACTEMENT comme {@code OrderStatusMapper.OUTBOUND_STATUS} (donc réconcilie avec
     * orders/list). {@code total} = somme des buckets. {@code returned} = 0 (pas d'état natif).
     * Anciennement : buckets dupliqués (out_for_delivery=pending, confirmed=processing) et total
     * scopé par période -> compteurs faux/incohérents.
     */
    public Map<String, Object> orderStatistics(Map<StatutCommande, Long> countsByStatut) {
        long pending = c(countsByStatut, StatutCommande.EN_ATTENTE);
        long confirmed = c(countsByStatut, StatutCommande.CONFIRMEE);
        long processing = c(countsByStatut, StatutCommande.EN_PREPARATION);
        long ready = c(countsByStatut, StatutCommande.PRETE);
        long outForDelivery = c(countsByStatut, StatutCommande.ASSIGNEE_LIVREUR) + c(countsByStatut, StatutCommande.EN_COURS);
        long delivered = c(countsByStatut, StatutCommande.LIVREE);
        long canceled = c(countsByStatut, StatutCommande.ANNULEE);
        long failed = c(countsByStatut, StatutCommande.NON_FINALISEE);
        long total = pending + confirmed + processing + ready + outForDelivery + delivered + canceled + failed;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", pending);
        m.put("confirmed", confirmed);
        m.put("processing", processing);
        m.put("ready", ready);
        m.put("out_for_delivery", outForDelivery);
        m.put("delivered", delivered);
        m.put("canceled", canceled);
        m.put("returned", 0L);
        m.put("failed", failed);
        m.put("total", total);
        return m;
    }

    private static long c(Map<StatutCommande, Long> counts, StatutCommande s) {
        Long v = counts.get(s);
        return v != null ? v : 0L;
    }

    /**
     * GET get-earning-statitics?type= -> objet gains 6valley. mysugu n'a pas d'historique CA
     * jour-par-jour/mois-par-mois pour un restaurant : la série est TOUJOURS synthétisée à
     * partir des 4 buckets du dashboard (aujourd'hui/semaine/mois/total), quel que soit
     * {@code type} (spec §3.2 — approximation documentée, jamais de faux points de série
     * fabriqués). Clés {@code seller_earn}/{@code commission_earn} (tableaux MAD, longueur 4)
     * confirmées via Tiktak-vendor-app-moso (bank_info_controller.dart::getDashboardRevenueData,
     * 3j.0) — l'app lit ces clés au premier niveau, PAS un nesting {@code series.*} (correction
     * par rapport à la conception initiale de la spec). {@code type} accepte les deux
     * conventions observées : {@code this_year/this_month/this_week} (spec) ET
     * {@code yearEarn/MonthEarn/WeekEarn} (valeurs réellement envoyées par
     * {@code BankInfoController.setRevenueFilterName}). Inconnu/absent -> "today".
     * <p>Les commissions viennent des cumuls portés par le dashboard quand ils sont
     * renseignés : c'est le montant réellement prélevé, seul juste depuis qu'une commission
     * peut être un montant fixe ou provenir du barème global sous le seuil de prix. Le calcul
     * {@code CA × taux} ne subsiste que comme repli, pour les appelants qui ne fournissent pas
     * ces cumuls. {@code commissionRatePercent} = {@code Restaurant.commissionPourcentage}
     * (peut être null -> 0) ; montants MAD, aucune conversion.
     */
    public Map<String, Object> earningStatistics(RestaurantDashboardDTO dto, String type, BigDecimal commissionRatePercent) {
        BigDecimal today = nzBd(dto.getChiffreAffairesAujourdhui());
        BigDecimal week = nzBd(dto.getChiffreAffairesSemaine());
        BigDecimal month = nzBd(dto.getChiffreAffairesMois());
        BigDecimal total = nzBd(dto.getTotalChiffreAffaires());
        BigDecimal rate = commissionRatePercent != null ? commissionRatePercent : BigDecimal.ZERO;

        BigDecimal commissionToday = commissionReelleOuEstimee(dto.getCommissionAujourdhui(), today, rate);
        BigDecimal commissionWeek = commissionReelleOuEstimee(dto.getCommissionSemaine(), week, rate);
        BigDecimal commissionMonth = commissionReelleOuEstimee(dto.getCommissionMois(), month, rate);
        BigDecimal commissionTotal = commissionReelleOuEstimee(dto.getTotalCommission(), total, rate);

        BigDecimal scalar = switch (normalizeEarningType(type)) {
            case "this_year" -> total; // pas de bucket annuel natif -> CA global (approximation)
            case "this_month" -> month;
            case "this_week" -> week;
            default -> today;
        };

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_earning", total);
        m.put("this_year", total);
        m.put("commission_earning", commissionTotal);
        m.put("earning", scalar);
        // APPROX: 6valley earning series synthesized from dashboard buckets (spec §3.2).
        m.put("seller_earn", List.of(today, week, month, total));
        m.put("commission_earn", List.of(
                commissionToday, commissionWeek, commissionMonth, commissionTotal));
        return m;
    }

    /** Le cumul réel s'il est fourni, sinon l'ancienne estimation {@code CA × taux}. */
    private static BigDecimal commissionReelleOuEstimee(BigDecimal reelle, BigDecimal ca, BigDecimal ratePercent) {
        return reelle != null ? reelle : commission(ca, ratePercent);
    }

    private String normalizeEarningType(String type) {
        if (type == null || type.isBlank()) {
            return "today";
        }
        return switch (type.trim().toLowerCase()) {
            case "this_year", "yearearn", "year" -> "this_year";
            case "this_month", "monthearn", "month" -> "this_month";
            case "this_week", "weekearn", "week" -> "this_week";
            default -> "today";
        };
    }

    private static BigDecimal commission(BigDecimal amount, BigDecimal ratePercent) {
        return amount.multiply(ratePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nzBd(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
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

    /**
     * GET top-delivery-man -> enveloppe 6valley bénigne, TOUJOURS vide. DECISION 3j.3 : STUB,
     * pas de réutilisation de {@code StatistiquesService.getTopLivreurs} — deux raisons : (1)
     * ce service est admin-GLOBAL (toutes plateformes confondues), pas scopé par restaurant, donc
     * l'afficher à un seul vendeur fuiterait/fausserait un classement plateforme qui ne le
     * concerne pas (umbrella §7.3) ; (2) l'enveloppe réelle confirmée via Tiktak-vendor-app-moso
     * (TopDeliveryManModel.fromJson / DeliveryMan.fromJson, 3j.0) attend des champs que mysugu ne
     * modélise pas pour un livreur (identity_number/identity_type/identity_image, rating[],
     * is_online non-null obligatoire sous peine de crash {@code int.parse(null)} côté app). Un
     * widget vide est rendu (jamais 500/404) ; {@code total_size/limit/offset/delivery_man}
     * confirmés — PAS un tableau nu comme la spec le supposait initialement (correction 3j.0).
     */
    public Map<String, Object> topDeliveryManStub() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", 0);
        m.put("limit", "10");
        m.put("offset", "0");
        m.put("delivery_man", List.of());
        return m;
    }
}
