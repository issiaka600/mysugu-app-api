package ma.mysuguclientapp.legacy.seller.mapper;

import ma.mysuguclientapp.dtos.NotificationDTO;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduit {@link NotificationDTO} natif (Page paginée, {@code lue}) -> le contrat 6valley
 * "notification" consommé par le tableau de notifications de Tiktak-vendor-app-moso, derrière
 * {@code /api/v3/seller/notification*}. Clés confirmées via
 * {@code lib/features/notification/domain/models/notification_model.dart} (3j.0) :
 * l'enveloppe expose {@code notification} (SINGULIER — NotificationItemModel.fromJson lit
 * {@code json['notification']}, pas {@code notifications} comme la conception initiale de la
 * spec le supposait) et chaque item expose {@code notification_seen_status}
 * (NotificationItem.fromJson lit {@code json['notification_seen_status']}, pas {@code is_read}).
 * {@code is_read} est conservé en bonus (inoffensif, aligné avec la spec §3.4 / les autres
 * enveloppes 6valley du shim). Voir
 * docs/superpowers/specs/2026-07-10-vendor-3j-stats-notifications-design.md §3.3/§3.4.
 */
@Component
public class SellerNotificationMapper {

    /**
     * {@code Page<NotificationDTO>} -> enveloppe 6valley
     * {total_size, limit, offset, new_notification, notification:[...]}.
     */
    public Map<String, Object> envelope(Page<NotificationDTO> page, int limit, int offset, long unseenCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", page.getTotalElements());
        m.put("limit", String.valueOf(limit));
        m.put("offset", String.valueOf(offset));
        m.put("new_notification", unseenCount);
        m.put("notification", page.getContent().stream().map(this::item).toList());
        return m;
    }

    /** {@link NotificationDTO} -> item 6valley (spec §3.4, clés réelles confirmées 3j.0). */
    public Map<String, Object> item(NotificationDTO dto) {
        int seen = Boolean.TRUE.equals(dto.getLue()) ? 1 : 0;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entity_id", dto.getEntityId());
        data.put("entity_type", dto.getEntityType());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dto.getId());
        m.put("title", dto.getTitre());
        m.put("description", dto.getMessage());
        m.put("type", dto.getType());
        m.put("image", null);
        m.put("notification_seen_status", seen);
        m.put("is_read", seen);
        m.put("seen_at", dto.getLueAt() != null ? dto.getLueAt().toString() : null);
        m.put("data", data);
        m.put("created_at", dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null);
        return m;
    }

    /** Accusé d'écriture 6valley (notification/view : l'app ne lit que le statusCode). */
    public Map<String, Object> success(String message) {
        return Map.of("message", message);
    }
}
