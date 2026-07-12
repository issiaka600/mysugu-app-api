package ma.mysuguclientapp.legacy.seller.mapper;

import ma.mysuguclientapp.dtos.commerce.CodePromoCreateDTO;
import ma.mysuguclientapp.dtos.commerce.CodePromoDTO;
import ma.mysuguclientapp.enumerations.TypeReduction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduit {@link CodePromoDTO} natif <-> le contrat 6valley "coupon" consommé par
 * Tiktak-vendor-app-moso (lib/features/coupon/domain/models/coupon_model.dart) derrière
 * {@code /api/v3/seller/coupon/*}. Pure traduction de forme — aucune persistance ni contrôle
 * d'appartenance (ceux-ci vivent dans le contrôleur via SellerContext / la garde createdBy).
 * Voir docs/superpowers/specs/2026-07-10-vendor-3h-coupons-design.md §5.
 */
@Component
public class SellerCouponMapper {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Formulaire 6valley (store/update) -> {@link CodePromoCreateDTO}. {@code discount_type} :
     * {@code percentage}->POURCENTAGE, {@code amount}->MONTANT_FIXE. Champs 6valley sans équivalent
     * natif (coupon_type, customer_id, coupon_bearer) acceptés puis ignorés (scoping §3).
     */
    public CodePromoCreateDTO toCreateDTO(Map<String, Object> form) {
        CodePromoCreateDTO dto = new CodePromoCreateDTO();
        dto.setCode(str(form.get("code")));
        // 6valley a title + code ; on mappe title -> description native (echo côté sortie).
        dto.setDescription(str(form.get("title")));
        dto.setTypeReduction(toTypeReduction(str(form.get("discount_type"))));
        dto.setValeur(bd(form.get("discount")));
        dto.setMontantMinCommande(bd(form.get("min_purchase")));
        dto.setMontantMaxReduction(bd(form.get("max_discount")));
        dto.setDateDebut(date(form.get("start_date")));
        dto.setDateFin(date(form.get("expire_date")));
        dto.setUsageMax(intOrNull(form.get("limit")));
        return dto;
    }

    /** {@link CodePromoDTO} natif -> JSON coupon 6valley (coupon_model.dart). */
    public Map<String, Object> toSixValley(CodePromoDTO dto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dto.getId());
        m.put("added_by", "seller");
        // mysugu n'a pas de taxonomie coupon_type ; valeur 6valley bénigne.
        m.put("coupon_type", "discount_on_purchase");
        m.put("coupon_bearer", "inhouse");
        m.put("seller_id", null);
        m.put("customer_id", null);
        m.put("title", dto.getDescription() != null ? dto.getDescription() : dto.getCode());
        m.put("code", dto.getCode());
        m.put("start_date", dto.getDateDebut() != null ? dto.getDateDebut().toLocalDate().format(DATE) : null);
        m.put("expire_date", dto.getDateFin() != null ? dto.getDateFin().toLocalDate().format(DATE) : null);
        // fromJson fait min_purchase/max_discount/discount .toDouble() -> jamais null.
        m.put("min_purchase", dbl(dto.getMontantMinCommande()));
        m.put("max_discount", dbl(dto.getMontantMaxReduction()));
        m.put("discount", dbl(dto.getValeur()));
        m.put("discount_type", dto.getTypeReduction() == TypeReduction.POURCENTAGE ? "percentage" : "amount");
        m.put("status", Boolean.TRUE.equals(dto.getIsActive()) ? 1 : 0);
        m.put("created_at", dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null);
        m.put("updated_at", dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null);
        m.put("limit", dto.getUsageMax());
        m.put("order_count", dto.getUsageCount() != null ? dto.getUsageCount() : 0);
        return m;
    }

    /** Enveloppe de pagination 6valley : {total_size, limit, offset, coupons:[...]}. */
    public Map<String, Object> listEnvelope(List<Map<String, Object>> coupons, int total, int limit, int offset) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", total);
        m.put("limit", String.valueOf(limit));
        m.put("offset", String.valueOf(offset));
        m.put("coupons", coupons);
        return m;
    }

    /** Accusé d'écriture 6valley. */
    public Map<String, Object> success(String message) {
        return Map.of("message", message);
    }

    private TypeReduction toTypeReduction(String discountType) {
        if (discountType == null) {
            return TypeReduction.MONTANT_FIXE;
        }
        return switch (discountType.trim().toLowerCase()) {
            case "percentage", "percent", "pourcentage" -> TypeReduction.POURCENTAGE;
            default -> TypeReduction.MONTANT_FIXE;
        };
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static BigDecimal bd(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(o.toString().trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valeur numérique invalide: " + o);
        }
    }

    private static double dbl(BigDecimal b) {
        return b != null ? b.doubleValue() : 0.0;
    }

    private static Integer intOrNull(Object o) {
        if (o == null || o.toString().isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limite invalide: " + o);
        }
    }

    private static LocalDateTime date(Object o) {
        if (o == null || o.toString().isBlank()) {
            return null;
        }
        String s = o.toString().trim();
        try {
            return LocalDate.parse(s).atStartOfDay();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(s);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date invalide: " + s);
            }
        }
    }
}
