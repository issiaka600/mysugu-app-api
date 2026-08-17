package ma.mysuguclientapp.legacy.seller.mapper;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.LigneCommandeDTO;
import ma.mysuguclientapp.dtos.LocalisationDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates native {@link CommandeDTO} <-> the 6valley "order" / "order-details" contract
 * consumed by Tiktak-vendor-app-moso (lib/features/order/domain/models/order_model.dart,
 * lib/features/order_details/domain/models/order_details_model.dart) behind
 * {@code /api/v3/seller/orders/*}. Pure shape translation — no persistence, no ownership
 * checks (those live in the controller via SellerContext). See
 * docs/superpowers/specs/2026-07-10-vendor-3d-orders-design.md §4.
 */
@Component
@RequiredArgsConstructor
public class OrderSellerMapper {

    private final OrderStatusMapper statusMapper;
    private final ProductSellerMapper productMapper;

    /** Native CommandeDTO -> 6valley Order JSON (order_model.dart:192). */
    public Map<String, Object> toOrder(CommandeDTO dto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dto.getId());
        m.put("order_status", statusMapper.toSixValleyStatus(parseStatut(dto.getStatut())));
        m.put("payment_status", statusMapper.toSixValleyPayment(parseStatutPaiement(dto.getStatutPaiement())));
        m.put("payment_method", dto.getMethodePaiement());
        // Never-null: the app calls .toDouble() on these (spec §4).
        m.put("order_amount", nonNull(dto.getMontantFinal()));
        m.put("shipping_cost", nonNull(dto.getFraisLivraison()));
        m.put("discount_amount", nonNull(dto.getMontantRemise()));
        m.put("montant_vendeur", nonNull(dto.getMontantVendeur()));
        m.put("montant_commission_total", nonNull(dto.getMontantCommissionTotal()));
        m.put("delivery_man_id", dto.getLivreur() != null ? dto.getLivreur().getId() : null);
        m.put("delivery_man", toDeliveryMan(dto.getLivreur()));
        m.put("expected_delivery_date", dto.getDateLivraisonPrevue() != null ? dto.getDateLivraisonPrevue().toString() : null);
        // GAP: no native "deliveryman_charge" (app-only field, distinct from fraisLivraison) — benign 0.
        m.put("deliveryman_charge", BigDecimal.ZERO);
        m.put("delivery_service_name", dto.getLivreurTiersNom());
        // GAP: no native home for a third-party tracking id (spec §6) — always null.
        m.put("third_party_delivery_tracking_id", null);
        m.put("order_note", dto.getCommentaire());
        m.put("canceled_by", dto.getCanceledBy());
        m.put("cancellation_reason", dto.getCancellationReason());
        m.put("canceled_at", dto.getCanceledAt() != null ? dto.getCanceledAt().toString() : null);
        m.put("order_type", dto.getModeReception());
        m.put("delivery_type", dto.getModeReception());
        // GAP: CommandeDTO does not expose codeVerificationLivraison (entity-only field) — benign null.
        m.put("verification_code", null);
        m.put("created_at", dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null);
        m.put("updated_at", dto.getUpdatedAt() != null ? dto.getUpdatedAt().toString() : null);
        Map<String, Object> address = toAddressData(dto.getAdresseLivraison());
        m.put("shipping_address_data", address);
        m.put("billing_address_data", address);
        m.put("customer", toCustomer(dto.getClient()));
        m.put("currency", dto.getCurrency() != null ? dto.getCurrency() : "MAD");
        m.put("is_shipping_free", false);
        m.put("is_guest", false);
        return m;
    }

    /** 6valley pagination envelope: {total_size, limit, offset, orders:[...]}. */
    public Map<String, Object> toListEnvelope(Page<CommandeDTO> page, int limit, int offset) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", (int) page.getTotalElements());
        m.put("limit", limit);
        m.put("offset", offset);
        m.put("orders", page.getContent().stream().map(this::toOrder).toList());
        return m;
    }

    /**
     * One line-item map per {@link LigneCommandeDTO}, each embedding the full {@code order}
     * block and {@code product_details} (order_details_model.dart:53). Reuses
     * {@link ProductSellerMapper#toSixValley} for product_details (products-slice 3c).
     */
    public List<Map<String, Object>> toOrderDetails(CommandeDTO dto) {
        Map<String, Object> order = toOrder(dto);
        List<LigneCommandeDTO> lignes = dto.getLignesCommande();
        if (lignes == null) {
            return List.of();
        }
        return lignes.stream().map(ligne -> toLineItem(dto, ligne, order)).toList();
    }

    private Map<String, Object> toLineItem(CommandeDTO dto, LigneCommandeDTO ligne, Map<String, Object> order) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ligne.getId());
        m.put("order_id", dto.getId());
        m.put("product_id", ligne.getPlat() != null ? ligne.getPlat().getId() : null);
        // GAP: owner not populated on the nested RestaurantDTO here — restaurant id stands in
        // for seller_id (spec §4).
        m.put("seller_id", dto.getRestaurant() != null ? dto.getRestaurant().getId() : null);
        m.put("qty", ligne.getQuantite());
        // Never-null: the app calls .toDouble() on price/tax/discount (spec §4).
        m.put("price", nonNull(ligne.getPrixUnitaire()));
        m.put("tax", BigDecimal.ZERO);
        m.put("discount", BigDecimal.ZERO);
        m.put("delivery_status", order.get("order_status"));
        m.put("payment_status", order.get("payment_status"));
        m.put("variant", null);
        m.put("variation", "");
        m.put("product_details", ligne.getPlat() != null ? productMapper.toSixValley(ligne.getPlat()) : null);
        m.put("order", order);
        m.put("verification_images", List.of());
        m.put("refund_request", null);
        return m;
    }

    private Map<String, Object> toDeliveryMan(UserDTO livreur) {
        if (livreur == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", livreur.getId());
        m.put("f_name", livreur.getPrenom());
        m.put("l_name", livreur.getNom());
        m.put("phone", livreur.getTelephone());
        m.put("email", livreur.getEmail());
        return m;
    }

    private Map<String, Object> toCustomer(UserDTO client) {
        if (client == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", client.getId());
        m.put("f_name", client.getPrenom());
        m.put("l_name", client.getNom());
        m.put("email", client.getEmail());
        m.put("phone", client.getTelephone());
        return m;
    }

    /** BillingAddressData shape (order_model.dart:429) — latitude/longitude are Strings. */
    private Map<String, Object> toAddressData(LocalisationDTO adresse) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("address", adresse != null ? adresse.getAdresse() : null);
        m.put("latitude", adresse != null && adresse.getLatitude() != null ? String.valueOf(adresse.getLatitude()) : null);
        m.put("longitude", adresse != null && adresse.getLongitude() != null ? String.valueOf(adresse.getLongitude()) : null);
        m.put("contact_person_name", null);
        m.put("phone", null);
        m.put("city", adresse != null ? adresse.getVille() : null);
        m.put("zip", adresse != null ? adresse.getCodePostal() : null);
        return m;
    }

    private BigDecimal nonNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private StatutCommande parseStatut(String statut) {
        if (statut == null) {
            return StatutCommande.EN_ATTENTE;
        }
        try {
            return StatutCommande.valueOf(statut);
        } catch (IllegalArgumentException e) {
            return StatutCommande.EN_ATTENTE;
        }
    }

    private StatutPaiement parseStatutPaiement(String statutPaiement) {
        if (statutPaiement == null) {
            return StatutPaiement.EN_ATTENTE;
        }
        try {
            return StatutPaiement.valueOf(statutPaiement);
        } catch (IllegalArgumentException e) {
            return StatutPaiement.EN_ATTENTE;
        }
    }
}
