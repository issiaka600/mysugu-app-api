package ma.mysuguclientapp.legacy.deliveryman.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutReglementCommission;
import ma.mysuguclientapp.enumerations.StatutRetrait;
import ma.mysuguclientapp.enumerations.TypeTransactionCaisse;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.deliveryman.dto.MessageResponse;
import ma.mysuguclientapp.legacy.deliveryman.mapper.LegacyOrderMapper;
import ma.mysuguclientapp.legacy.deliveryman.service.DeliveryManInfoService;
import ma.mysuguclientapp.repositories.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Endpoints argent du livreur (contrat 6valley §5.6–§5.10). Modèle argent : techspec §7.
 * - delivery-wise-earned / collected_cash_history : lectures paginées
 * - withdraw-request / withdraw-list-by-approved : retraits bancaires (DemandeRetrait)
 * - commission/{type} : cut plateforme informatif (15% des frais), bookkeeping ReglementCommission
 */
@RestController
@RequestMapping("/api/v2/delivery-man")
@RequiredArgsConstructor
public class DeliveryManMoneyController {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.15");
    private static final DateTimeFormatter D_MY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final CommandeRepository commandeRepository;
    private final CaisseLivreurRepository caisseLivreurRepository;
    private final TransactionCaisseRepository transactionCaisseRepository;
    private final DemandeRetraitRepository demandeRetraitRepository;
    private final ReglementCommissionRepository reglementCommissionRepository;
    private final DeliveryManInfoService infoService;
    private final LegacyOrderMapper mapper;

    // ---------- delivery-wise-earned ----------
    @GetMapping("/delivery-wise-earned")
    public Map<String, Object> deliveryWiseEarned(@AuthenticationPrincipal String email,
                                                  @RequestParam(required = false) String type,
                                                  @RequestParam(name = "start_date", required = false) String startDate,
                                                  @RequestParam(name = "end_date", required = false) String endDate,
                                                  @RequestParam(defaultValue = "10") int limit,
                                                  @RequestParam(defaultValue = "1") int offset) {
        User l = livreur(email);
        LocalDateTime[] range = earnRange(type, startDate, endDate);
        List<Commande> paid = commandeRepository.findByLivreurIdOrderByCreatedAtDesc(l.getId()).stream()
                .filter(c -> c.getStatutPaiement() == ma.mysuguclientapp.enumerations.StatutPaiement.PAYE)
                .filter(c -> inRange(c.getCreatedAt(), range))
                .toList();
        List<Map<String, Object>> orders = page(paid, limit, offset).stream()
                .map(c -> mapper.toOrderMap(c, false)).toList();
        return wrap("orders", orders, paid.size(), limit, offset);
    }

    // ---------- collected_cash_history ----------
    @GetMapping("/collected_cash_history")
    public Map<String, Object> collectedCashHistory(@AuthenticationPrincipal String email,
                                                    @RequestParam(defaultValue = "10") int limit,
                                                    @RequestParam(defaultValue = "1") int offset) {
        User l = livreur(email);
        CaisseLivreur caisse = caisseLivreurRepository.findByLivreurId(l.getId()).orElse(null);
        if (caisse == null) return wrap("deposit", List.of(), 0, limit, offset);
        var pageReq = PageRequest.of(Math.max(0, offset - 1), Math.max(1, limit));
        var pageResult = transactionCaisseRepository.findByCaisseLivreurIdAndTypeOrderByCreatedAtDesc(
                caisse.getId(), TypeTransactionCaisse.REMISE_PLATEFORME, pageReq);
        List<Map<String, Object>> deposit = pageResult.getContent().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("delivery_man_id", l.getId());
            m.put("user_id", 0);
            m.put("user_type", "admin");
            m.put("credit", t.getMontant());
            m.put("transaction_type", "cash_in_hand");
            m.put("created_at", t.getCreatedAt() != null ? t.getCreatedAt().format(TS) : null);
            m.put("updated_at", t.getCreatedAt() != null ? t.getCreatedAt().format(TS) : null);
            return m;
        }).toList();
        return wrap("deposit", deposit, (int) pageResult.getTotalElements(), limit, offset);
    }

    // ---------- withdraw-request ----------
    @PostMapping("/withdraw-request")
    public ResponseEntity<?> withdrawRequest(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        BigDecimal amount;
        try {
            amount = new BigDecimal(str(body.get("amount")));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponse("Montant invalide."));
        }
        if (amount.compareTo(BigDecimal.ONE) <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponse("Le montant doit être supérieur à 1."));
        }
        BigDecimal withdrawable = infoService.withdrawable(l.getId());
        if (amount.compareTo(withdrawable) > 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageResponse("Le montant ne peut pas dépasser le solde retirable."));
        }
        demandeRetraitRepository.save(DemandeRetrait.builder()
                .livreur(l).montant(amount).note(str(body.get("note")))
                .statut(StatutRetrait.EN_ATTENTE).build());
        return ResponseEntity.ok(new MessageResponse("Demande de retrait envoyée avec succès."));
    }

    // ---------- withdraw-list-by-approved ----------
    @GetMapping("/withdraw-list-by-approved")
    public Map<String, Object> withdrawList(@AuthenticationPrincipal String email,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(defaultValue = "10") int limit,
                                            @RequestParam(defaultValue = "1") int offset) {
        User l = livreur(email);
        StatutRetrait statut = "withdrawn".equalsIgnoreCase(type) ? StatutRetrait.APPROUVE : StatutRetrait.EN_ATTENTE;
        var pageReq = PageRequest.of(Math.max(0, offset - 1), Math.max(1, limit));
        var pageResult = demandeRetraitRepository.findByLivreurIdAndStatutOrderByCreatedAtDesc(l.getId(), statut, pageReq);
        List<Map<String, Object>> withdraws = pageResult.getContent().stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("amount", d.getMontant());           // fragile : numérique, toujours présent
            m.put("transaction_note", d.getNote());
            m.put("created_at", d.getCreatedAt() != null ? d.getCreatedAt().format(TS) : null);
            m.put("updated_at", d.getUpdatedAt() != null ? d.getUpdatedAt().format(TS) : null);
            return m;
        }).toList();
        return wrap("withdraws", withdraws, (int) pageResult.getTotalElements(), limit, offset);
    }

    // ---------- commission ----------
    @GetMapping("/commission/{type}")
    public Map<String, Object> commissionSummary(@AuthenticationPrincipal String email,
                                                 @PathVariable String type,
                                                 @RequestParam(name = "start_date", required = false) String startDate,
                                                 @RequestParam(name = "end_date", required = false) String endDate) {
        User l = livreur(email);
        return Map.of("success", true, "commissions", buildCommission(l, type, startDate, endDate, false));
    }

    @PostMapping("/commission/{type}")
    public Map<String, Object> commissionMarkPaid(@AuthenticationPrincipal String email,
                                                  @PathVariable String type,
                                                  @RequestParam(name = "start_date", required = false) String startDate,
                                                  @RequestParam(name = "end_date", required = false) String endDate,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        User l = livreur(email);
        Map<String, Object> commissions = buildCommission(l, type, startDate, endDate, true);
        if (body != null && body.get("transaction_ref") != null) {
            commissions.put("transaction_ref", body.get("transaction_ref"));
        }
        return Map.of("success", true, "commissions", commissions);
    }

    /** Calcule le résumé de commission (cut plateforme informatif). markPaid=true insère un ReglementCommission PAID. */
    private Map<String, Object> buildCommission(User l, String type, String startDate, String endDate, boolean markPaid) {
        LocalDate[] days = commissionRange(type, startDate, endDate);
        LocalDateTime debut = days[0].atStartOfDay();
        LocalDateTime fin = days[1].atTime(23, 59, 59);

        List<Commande> livrees = commandeRepository.findByLivreurIdOrderByCreatedAtDesc(l.getId()).stream()
                .filter(c -> c.getStatut() == StatutCommande.LIVREE)
                .filter(c -> inRange(c.getCreatedAt(), new LocalDateTime[]{debut, fin}))
                .toList();

        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> details = new ArrayList<>();
        for (Commande c : livrees) {
            BigDecimal frais = c.getFraisLivraison() != null ? c.getFraisLivraison() : BigDecimal.ZERO;
            BigDecimal commission = frais.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
            total = total.add(commission);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("order_id", c.getId());
            d.put("order_amount", c.getMontantFinal() != null ? c.getMontantFinal() : c.getMontantTotal());
            d.put("commission_amount", commission);
            d.put("date", c.getCreatedAt() != null ? c.getCreatedAt().format(TS) : null);
            details.add(d);
        }

        var existing = reglementCommissionRepository.findByLivreurIdAndStartDateAndEndDate(l.getId(), days[0], days[1]);
        String status = existing.map(r -> r.getStatut().name().toLowerCase()).orElse("pending");

        if (markPaid && existing.isEmpty() && total.compareTo(BigDecimal.ZERO) > 0) {
            reglementCommissionRepository.save(ReglementCommission.builder()
                    .livreur(l).startDate(days[0]).endDate(days[1]).montant(total)
                    .statut(StatutReglementCommission.PAID).payeAt(LocalDateTime.now()).build());
            status = "paid";
        }

        Map<String, Object> commissions = new LinkedHashMap<>();
        commissions.put("total", total);
        commissions.put("montant_to_pay", total);
        commissions.put("start_date", days[0].format(D_MY));
        commissions.put("end_date", days[1].format(D_MY));
        commissions.put("period", type);
        commissions.put("status", status);
        commissions.put("details", details);
        return commissions;
    }

    // ---------- helpers ----------

    private LocalDateTime[] earnRange(String type, String start, String end) {
        LocalDateTime now = LocalDateTime.now();
        if (start != null && !start.isBlank() && end != null && !end.isBlank()) {
            return new LocalDateTime[]{parseDate(start).atStartOfDay(), parseDate(end).atTime(23, 59, 59)};
        }
        if (type == null) return null;
        return switch (type) {
            case "TodayEarn" -> new LocalDateTime[]{LocalDate.now().atStartOfDay(), now};
            case "ThisWeekEarn" -> new LocalDateTime[]{now.minusDays(7), now};
            case "ThisMonthEarn" -> new LocalDateTime[]{now.minusDays(30), now};
            default -> null;
        };
    }

    private LocalDate[] commissionRange(String type, String start, String end) {
        LocalDate today = LocalDate.now();
        return switch (type == null ? "" : type) {
            case "today" -> new LocalDate[]{today, today};
            case "week" -> new LocalDate[]{today.minusDays(6), today};
            case "month" -> new LocalDate[]{today.minusDays(29), today};
            case "last_10_days" -> new LocalDate[]{today.minusDays(9), today};
            case "custom" -> new LocalDate[]{
                    start != null ? parseDate(start) : today.minusDays(9),
                    end != null ? parseDate(end) : today};
            default -> new LocalDate[]{today.minusDays(29), today};
        };
    }

    private static LocalDate parseDate(String s) {
        s = s.trim();
        for (DateTimeFormatter f : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"))) {
            try { return LocalDate.parse(s, f); } catch (Exception ignore) { }
        }
        return LocalDate.now();
    }

    private static boolean inRange(LocalDateTime dt, LocalDateTime[] range) {
        if (range == null) return true;
        if (dt == null) return false;
        return !dt.isBefore(range[0]) && !dt.isAfter(range[1]);
    }

    private static <T> List<T> page(List<T> list, int limit, int offset) {
        int from = Math.max(0, (offset - 1) * limit);
        if (from >= list.size()) return List.of();
        return list.subList(from, Math.min(list.size(), from + limit));
    }

    private static Map<String, Object> wrap(String key, List<?> items, int totalSize, int limit, int offset) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", totalSize);
        m.put("limit", String.valueOf(limit));
        m.put("offset", String.valueOf(offset));
        m.put(key, items);
        return m;
    }

    private User livreur(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Livreur non authentifié"));
        if (u.getRole() != UserRole.LIVREUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux livreurs");
        }
        return u;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
