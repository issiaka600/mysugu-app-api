package ma.mysuguclientapp.legacy.deliveryman.mapper;

import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.enumerations.MethodePaiement;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper Commande (natif MySugu) -> objet "order" 6valley attendu par l'app Tiktak.
 * Réutilisé par current-orders / all-orders / order-details / accept / delivery-wise-earned.
 * Respecte les clés fragiles du contrat (docs/legacy-contracts/CONTRACT-REFERENCE.md §3) :
 *  - seller.shop.seller_id TOUJOURS présent et numérique (int.parse non gardé côté app)
 *  - shipping_address_data + billing_address_data TOUJOURS des objets (parsés sans garde en §5.6)
 */
@Component
public class LegacyOrderMapper {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ---- Mapping des statuts MySugu <-> 6valley ----

    public static String toLegacyStatus(StatutCommande s) {
        if (s == null) return "pending";
        return switch (s) {
            case EN_ATTENTE, NON_FINALISEE -> "pending";
            case CONFIRMEE -> "confirmed";
            case EN_PREPARATION -> "processing";
            case PRETE -> "ready";
            case ASSIGNEE_LIVREUR -> "assigned";
            case EN_COURS -> "out_for_delivery";
            case LIVREE -> "delivered";
            case ANNULEE -> "canceled";
            case RETOURNEE -> "returned";
            case ECHEC_LIVRAISON -> "failed";
        };
    }

    /** Statut 6valley (envoyé par update-order-status) -> StatutCommande natif. */
    public static StatutCommande fromLegacyStatus(String legacy) {
        if (legacy == null) return null;
        return switch (legacy.toLowerCase()) {
            case "out_for_delivery" -> StatutCommande.EN_COURS;
            case "delivered" -> StatutCommande.LIVREE;
            case "canceled" -> StatutCommande.ANNULEE;
            case "returned" -> StatutCommande.RETOURNEE;
            case "failed" -> StatutCommande.ECHEC_LIVRAISON;
            case "confirmed" -> StatutCommande.CONFIRMEE;
            case "processing" -> StatutCommande.EN_PREPARATION;
            case "pending" -> StatutCommande.EN_ATTENTE;
            default -> null;
        };
    }

    // ---- Mapping principal ----

    public Map<String, Object> toOrderMap(Commande c, boolean includeDetails) {
        return toOrderMap(c, includeDetails, null);
    }

    public Map<String, Object> toOrderMap(Commande c, boolean includeDetails, OffreLivraison offre) {
        Map<String, Object> m = new LinkedHashMap<>();
        Long sellerId = resolveSellerId(c);

        m.put("id", c.getId());
        m.put("customer_id", c.getClient() != null ? c.getClient().getId() : null);
        m.put("customer_type", "customer");
        String status = toLegacyStatus(c.getStatut());
        m.put("order_status", status);
        m.put("status", status);
        m.put("payment_status", c.getStatutPaiement() == StatutPaiement.PAYE ? "paid" : "unpaid");
        m.put("payment_method", c.getMethodePaiement() == MethodePaiement.ESPECES ? "cash_on_delivery" : "digital_payment");
        m.put("transaction_ref", c.getStripePaymentIntentId());
        BigDecimal montantFinal = nz(c.getMontantFinal() != null ? c.getMontantFinal() : c.getMontantTotal());
        BigDecimal montantVendeur = montantVendeur(c, montantFinal);
        m.put("order_amount", montantFinal);
        m.put("montantFinal", montantFinal);
        m.put("deliveryman_charge", nz(c.getFraisLivraison()));
        m.put("shipping_cost", nz(c.getFraisLivraison()));
        m.put("discount_amount", nz(c.getMontantRemise()));
        m.put("montant_vendeur", montantVendeur);
        m.put("montant_commission_total", nz(c.getMontantCommissionTotal()));
        m.put("discount_type", "amount");
        m.put("coupon_code", c.getCodePromoUtilise());
        m.put("order_note", c.getCommentaire());
        m.put("cause", c.getRaisonAnnulation());
        m.put("canceled_by", normalizeCanceledBy(c.getCanceledBy()));
        m.put("cancellation_reason", c.getRaisonAnnulation());
        m.put("canceled_at", c.getCanceledAt() != null ? c.getCanceledAt().toString() : null);
        m.put("is_pause", Boolean.TRUE.equals(c.getEnPause()));
        m.put("is_guest", false);
        // Le code appartient au client et ne doit jamais être révélé au livreur.
        m.put("verification_code", null);
        m.put("delivery_man_id", c.getLivreur() != null ? c.getLivreur().getId() : null);
        m.put("assignment_state", assignmentState(c, offre));
        m.put("delivery_offer_id", offre != null ? offre.getId() : null);
        m.put("offer_expires_at", offerExpiresAt(offre));
        m.put("offer_remaining_seconds", offerRemainingSeconds(offre));
        m.put("seller_id", sellerId);
        m.put("seller_is", "seller");
        m.put("shipping_method_id", 0);
        m.put("order_group_id", 0);
        m.put("expected_delivery_date", isoFmt(c.getDateLivraisonPrevue() != null ? c.getDateLivraisonPrevue() : c.getScheduledAt()));
        m.put("created_at", fmt(c.getCreatedAt()));
        m.put("updated_at", fmt(c.getUpdatedAt()));
        m.put("is_shipping_free", false);
        m.put("total_commission", nz(c.getMontantCommissionTotal()));
        m.put("seller_total", montantVendeur);
        // Écran "Infos de paiement" appli livreurs (correction PDF bug #10) :
        // total commande = total vendeur + commission (prix produits, hors livraison, hors remise admin)
        // commissions = notre commission ; total vendeur = déjà calculé ci-dessus (montant_vendeur)
        // remise = promotion automatique restaurant (toujours admin) ; coupon = code saisi par le client
        // total général = montant que le client paie au final (déjà exposé via order_amount/montantFinal)
        m.put("total_commande", montantVendeur.add(nz(c.getMontantCommissionTotal())));
        m.put("montant_remise_promotion", nz(c.getMontantRemisePromotion()));
        m.put("montant_coupon", nz(c.getMontantCoupon()));

        Map<String, Object> address = addressMap(c);
        m.put("shipping_address", address);
        m.put("shipping_address_data", address);   // fragile : objet toujours présent
        m.put("billing_address_data", address);     // fragile : objet toujours présent
        m.put("customer", customerMap(c.getClient()));
        m.put("seller", sellerMap(c.getRestaurant(), sellerId));

        if (includeDetails) {
            m.put("details", detailsList(c));
        }
        return m;
    }

    private String normalizeCanceledBy(String canceledBy) {
        if (canceledBy == null) return null;
        return switch (canceledBy.toLowerCase()) {
            case "vendor", "seller", "vendeur" -> "vendor";
            case "client", "customer" -> "client";
            default -> null;
        };
    }

    private String assignmentState(Commande commande, OffreLivraison offre) {
        if (offre != null) {
            return switch (offre.getStatut()) {
                case PROPOSEE -> "offered";
                case ACCEPTEE -> "accepted";
                case REFUSEE, ANNULEE -> "rejected";
                case EXPIREE -> "expired";
            };
        }
        return commande.getLivreur() != null ? "accepted" : null;
    }

    private String offerExpiresAt(OffreLivraison offre) {
        if (offre == null || offre.getStatut() != ma.mysuguclientapp.enumerations.StatutOffreLivraison.PROPOSEE
                || offre.getExpiresAt() == null) return null;
        return offre.getExpiresAt().toInstant(ZoneOffset.UTC).toString();
    }

    private long offerRemainingSeconds(OffreLivraison offre) {
        if (offre == null || offre.getStatut() != ma.mysuguclientapp.enumerations.StatutOffreLivraison.PROPOSEE
                || offre.getExpiresAt() == null) return 0L;
        return Math.max(0L, ChronoUnit.SECONDS.between(LocalDateTime.now(), offre.getExpiresAt()));
    }

    private Long resolveSellerId(Commande c) {
        Restaurant r = c.getRestaurant();
        if (r == null) return 0L;
        if (r.getOwner() != null) return r.getOwner().getId();
        return r.getId();
    }

    /**
     * Montant net vendeur : prix des plats/produits moins la commission, hors livraison.
     * Une remise financée par l'admin n'est pas déduite du montant vendeur (correction "résumé
     * de commande vendeur" / infos de paiement livreur, §3d et §3f) — voir la même logique dans
     * CommandeServiceImpl#calculerMontantVendeur.
     */
    private BigDecimal montantVendeur(Commande commande, BigDecimal montantFinal) {
        return montantFinal
                .subtract(nz(commande.getFraisLivraison()))
                .subtract(nz(commande.getMontantCommissionTotal()))
                .add(nz(commande.getMontantRemiseAdmin()))
                .max(BigDecimal.ZERO);
    }

    private Map<String, Object> customerMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (u == null) {
            m.put("id", 0);
            m.put("f_name", "");
            m.put("l_name", "");
            m.put("name", "");
            m.put("phone", "");
            m.put("email", "");
            m.put("image", "");
            return m;
        }
        m.put("id", u.getId());
        m.put("f_name", nvl(u.getPrenom()));
        m.put("l_name", nvl(u.getNom()));
        m.put("name", (nvl(u.getPrenom()) + " " + nvl(u.getNom())).trim());
        m.put("phone", nvl(u.getTelephone()));
        m.put("email", nvl(u.getEmail()));
        m.put("image", nvl(u.getAvatar()));
        return m;
    }

    private Map<String, Object> sellerMap(Restaurant r, Long sellerId) {
        Map<String, Object> seller = new LinkedHashMap<>();
        if (r == null) {
            seller.put("id", sellerId);
            seller.put("phone", "");
            seller.put("email", "");
            seller.put("shop", shopMap(null, sellerId));
            return seller;
        }
        seller.put("id", sellerId);
        seller.put("phone", r.getOwner() != null ? nvl(r.getOwner().getTelephone()) : "");
        seller.put("email", r.getOwner() != null ? nvl(r.getOwner().getEmail()) : "");
        seller.put("shop", shopMap(r, sellerId));
        return seller;
    }

    private Map<String, Object> shopMap(Restaurant r, Long sellerId) {
        Map<String, Object> shop = new LinkedHashMap<>();
        shop.put("id", r != null ? r.getId() : 0);
        shop.put("seller_id", sellerId != null ? sellerId : 0); // FRAGILE : numérique obligatoire
        shop.put("name", r != null ? nvl(r.getNom()) : "");
        shop.put("image", r != null ? nvl(r.getLogoUrl()) : "");
        if (r != null && r.getLocalisation() != null) {
            shop.put("latitude", r.getLocalisation().getLatitude());
            shop.put("longitude", r.getLocalisation().getLongitude());
            shop.put("address", nvl(r.getLocalisation().getAdresse()));
        } else {
            shop.put("latitude", null);
            shop.put("longitude", null);
            shop.put("address", "");
        }
        return shop;
    }

    private Map<String, Object> addressMap(Commande c) {
        Localisation loc = c.getAdresseLivraison();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", c.getId());
        a.put("customer_id", c.getClient() != null ? c.getClient().getId() : 0);
        a.put("contact_person_name", c.getClient() != null
                ? (nvl(c.getClient().getPrenom()) + " " + nvl(c.getClient().getNom())).trim() : "");
        a.put("contact_person_number", c.getClient() != null ? nvl(c.getClient().getTelephone()) : "");
        a.put("address_type", "home");
        a.put("address", loc != null ? nvl(loc.getAdresse()) : "");
        a.put("city", loc != null ? nvl(loc.getVille()) : "");
        a.put("zip", loc != null ? nvl(loc.getCodePostal()) : "");
        a.put("latitude", loc != null ? str(loc.getLatitude()) : "0");
        a.put("longitude", loc != null ? str(loc.getLongitude()) : "0");
        return a;
    }

    private List<Map<String, Object>> detailsList(Commande c) {
        List<Map<String, Object>> details = new ArrayList<>();
        if (c.getLignesCommande() == null) return details;
        for (LigneCommande l : c.getLignesCommande()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", l.getId());
            d.put("order_id", c.getId());
            d.put("product_id", l.getPlat() != null ? l.getPlat().getId() : 0);
            d.put("seller_id", resolveSellerId(c));
            d.put("qty", l.getQuantite());
            d.put("price", nz(l.getPrixUnitaire()));
            d.put("discount", BigDecimal.ZERO);
            d.put("tax", BigDecimal.ZERO);
            d.put("delivery_status", toLegacyStatus(c.getStatut()));
            d.put("payment_status", c.getStatutPaiement() == StatutPaiement.PAYE ? "paid" : "unpaid");
            d.put("variant", "");
            d.put("shipping_method_id", 0);
            d.put("shipping_free", false);
            d.put("created_at", fmt(c.getCreatedAt()));
            d.put("updated_at", fmt(c.getUpdatedAt()));
            Map<String, Object> product = new LinkedHashMap<>();
            product.put("id", l.getPlat() != null ? l.getPlat().getId() : 0);
            product.put("name", l.getPlat() != null ? nvl(l.getPlat().getNom()) : "");
            product.put("unit_price", nz(l.getPrixUnitaire()));
            product.put("tax", BigDecimal.ZERO);
            product.put("discount", BigDecimal.ZERO);
            product.put("images", new ArrayList<>());
            d.put("product_details", product);
            details.add(d);
        }
        return details;
    }

    private static String fmt(LocalDateTime dt) {
        return dt != null ? dt.format(TS) : null;
    }

    private static String isoFmt(LocalDateTime dt) {
        return dt != null ? dt.format(ISO_TS) : null;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private static String str(Object o) {
        return o == null ? "0" : o.toString();
    }
}
