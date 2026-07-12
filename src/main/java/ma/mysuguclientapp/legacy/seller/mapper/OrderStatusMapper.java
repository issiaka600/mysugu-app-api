package ma.mysuguclientapp.legacy.seller.mapper;

import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.Map;

/**
 * Translates native {@link StatutCommande} / {@link StatutPaiement} <-> the 6valley
 * {@code order_status} / {@code payment_status} strings consumed by
 * Tiktak-vendor-app-moso (order_details_repository.dart:33-54,73). Pure shape translation —
 * no persistence, no ownership checks (those live in the controller via SellerContext).
 * Normative mapping table: docs/superpowers/specs/2026-07-10-vendor-3d-orders-design.md §3/§3b.
 */
@Component
public class OrderStatusMapper {

    // ---- Outbound: native StatutCommande -> 6valley order_status (spec §3, lossy) ----
    private static final Map<StatutCommande, String> OUTBOUND_STATUS = new EnumMap<>(StatutCommande.class);
    static {
        OUTBOUND_STATUS.put(StatutCommande.EN_ATTENTE, "pending");
        OUTBOUND_STATUS.put(StatutCommande.CONFIRMEE, "confirmed");
        OUTBOUND_STATUS.put(StatutCommande.EN_PREPARATION, "processing");
        OUTBOUND_STATUS.put(StatutCommande.PRETE, "processing");
        OUTBOUND_STATUS.put(StatutCommande.ASSIGNEE_LIVREUR, "out_for_delivery");
        OUTBOUND_STATUS.put(StatutCommande.EN_COURS, "out_for_delivery");
        OUTBOUND_STATUS.put(StatutCommande.LIVREE, "delivered");
        OUTBOUND_STATUS.put(StatutCommande.ANNULEE, "canceled");
        OUTBOUND_STATUS.put(StatutCommande.NON_FINALISEE, "failed");
    }

    // ---- Inbound: 6valley order_status -> native StatutCommande (spec §3) ----
    private static final Map<String, StatutCommande> INBOUND_STATUS = Map.of(
            "pending", StatutCommande.EN_ATTENTE,
            "confirmed", StatutCommande.CONFIRMEE,
            "processing", StatutCommande.EN_PREPARATION,
            "out_for_delivery", StatutCommande.EN_COURS,
            "delivered", StatutCommande.LIVREE,
            "canceled", StatutCommande.ANNULEE,
            // native has no return/fail state (spec §6) — both collapse to ANNULEE
            "returned", StatutCommande.ANNULEE,
            "failed", StatutCommande.ANNULEE
    );

    // ---- Outbound: native StatutPaiement -> 6valley payment_status (spec §3b) ----
    private static final Map<StatutPaiement, String> OUTBOUND_PAYMENT = new EnumMap<>(StatutPaiement.class);
    static {
        OUTBOUND_PAYMENT.put(StatutPaiement.PAYE, "paid");
        OUTBOUND_PAYMENT.put(StatutPaiement.EN_ATTENTE, "unpaid");
        OUTBOUND_PAYMENT.put(StatutPaiement.ECHOUE, "unpaid");
        // refund surfaced in finances slice 3f — the app only understands paid/unpaid
        OUTBOUND_PAYMENT.put(StatutPaiement.REMBOURSE, "unpaid");
    }

    // ---- Inbound: 6valley payment_status -> native StatutPaiement (spec §3b) ----
    private static final Map<String, StatutPaiement> INBOUND_PAYMENT = Map.of(
            "paid", StatutPaiement.PAYE,
            "unpaid", StatutPaiement.EN_ATTENTE
    );

    public String toSixValleyStatus(StatutCommande statut) {
        String value = statut != null ? OUTBOUND_STATUS.get(statut) : null;
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut inconnu: " + statut);
        }
        return value;
    }

    public StatutCommande fromSixValleyStatus(String orderStatus) {
        StatutCommande value = orderStatus != null ? INBOUND_STATUS.get(orderStatus.trim().toLowerCase()) : null;
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "order_status inconnu: " + orderStatus);
        }
        return value;
    }

    public String toSixValleyPayment(StatutPaiement statutPaiement) {
        String value = statutPaiement != null ? OUTBOUND_PAYMENT.get(statutPaiement) : null;
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut de paiement inconnu: " + statutPaiement);
        }
        return value;
    }

    public StatutPaiement fromSixValleyPayment(String paymentStatus) {
        StatutPaiement value = paymentStatus != null ? INBOUND_PAYMENT.get(paymentStatus.trim().toLowerCase()) : null;
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payment_status inconnu: " + paymentStatus);
        }
        return value;
    }
}
