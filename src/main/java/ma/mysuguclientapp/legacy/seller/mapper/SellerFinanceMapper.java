package ma.mysuguclientapp.legacy.seller.mapper;

import ma.mysuguclientapp.dtos.caisse.PaiementRestaurantDTO;
import ma.mysuguclientapp.enumerations.ModeVersementRestaurant;
import ma.mysuguclientapp.enumerations.StatutPaiementRestaurant;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduit les données de facturation restaurant natives (seule donnée financière réelle
 * accessible en LECTURE par un {@code RESTAURANT_OWNER}, spec 3f §2/§4) vers le contrat 6valley
 * "finances" consommé par le tableau financier de Tiktak-vendor-app-moso, derrière
 * {@code /api/v3/seller/{transactions,withdraw-method-list,balance-withdraw,
 * close-withdraw-request,refund/*}}.
 *
 * <p><b>Déviations confirmées 3f.0 (Dart réel prime sur la conception initiale de la spec) :</b>
 * <ul>
 *   <li>{@code transactions} et {@code refund/list} : le corps de réponse est un TABLEAU JSON NU
 *       ({@code apiResponse.response!.data.forEach((row) => ...)} dans
 *       {@code transaction_controller.dart}/{@code refund_controller.dart}), PAS l'enveloppe
 *       {@code {total_size, limit, offset, transactions:[...]}}/{@code {..., refunds:[...]}}
 *       supposée par la conception initiale de la spec §3f. {@code withdraw-method-list} est
 *       également un tableau nu (déjà supposé correctement par la spec).</li>
 *   <li>{@code TransactionModel.fromJson} fait {@code int.parse(json['seller_id'].toString())} et
 *       {@code double.parse(json['amount'].toString())} SANS garde de nullité -> ces deux champs
 *       ne doivent JAMAIS être {@code null}. {@code created_at} est utilisé sans garde
 *       ({@code transaction.createdAt!}) par le filtre mensuel -> jamais {@code null} non plus.</li>
 *   <li>{@code WithdrawModel.fromJson} fait {@code json['is_active'] ? 1 : 0} SANS garde de
 *       nullité -> {@code is_active} doit toujours être un booléen JSON non nul.</li>
 *   <li>{@code RefundDetailsModel.fromJson} fait {@code json['<champ>'].toDouble()} SANS garde de
 *       nullité sur tous les champs numériques (product_price, product_total_discount,
 *       product_total_tax, subtotal, coupon_discount, refund_amount) -> jamais {@code null}
 *       (défaut {@code 0}), seul {@code quntity} (orthographe 6valley conservée) est lu sans
 *       {@code .toDouble()} et tolère {@code null}.</li>
 * </ul>
 * Voir docs/superpowers/specs/2026-07-10-vendor-3f-finances-design.md §4 et le rapport 3f.0.
 */
@Component
public class SellerFinanceMapper {

    /**
     * {@link PaiementRestaurantDTO} -> ligne "transaction" 6valley (tableau nu, spec 3f §4
     * corrigée 3f.0). {@code seller_id} = id du restaurant du vendeur (pas de notion de
     * "seller" distincte côté natif ici) ; {@code amount} = {@code montantTotal} (MAD
     * pass-through, umbrella §4) ; {@code approved} = 1 si {@code statut == EFFECTUE} sinon 0 ;
     * {@code created_at}/{@code amount}/{@code seller_id} ne sont JAMAIS null (contrainte Dart
     * ci-dessus).
     */
    public Map<String, Object> toTransactionRow(PaiementRestaurantDTO dto, Long restaurantId) {
        BigDecimal amount = dto.getMontantTotal() != null ? dto.getMontantTotal() : BigDecimal.ZERO;
        LocalDateTime createdAt = dto.getDatePaiement() != null ? dto.getDatePaiement()
                : (dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
        boolean approved = dto.getStatut() == StatutPaiementRestaurant.EFFECTUE;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dto.getId());
        m.put("seller_id", restaurantId);
        m.put("admin_id", null);
        m.put("amount", amount);
        m.put("transaction_note", dto.getNote() != null ? dto.getNote() : dto.getReference());
        m.put("approved", approved ? 1 : 0);
        m.put("created_at", createdAt.toString());
        m.put("updated_at", createdAt.toString());
        return m;
    }

    public List<Map<String, Object>> transactionList(List<PaiementRestaurantDTO> paiements, Long restaurantId) {
        return paiements.stream().map(p -> toTransactionRow(p, restaurantId)).collect(Collectors.toList());
    }

    /**
     * {@code ModeVersementRestaurant} -> ligne "withdraw method" 6valley bénigne (tableau nu ;
     * pas de méthodes configurables réellement, spec 3f §3 GAP). {@code method_fields} = tableau
     * vide non nul ; {@code is_active} TOUJOURS booléen non nul (contrainte Dart ci-dessus).
     * STUB: no configurable seller withdraw methods; enum echo only.
     */
    public Map<String, Object> withdrawMethodRow(ModeVersementRestaurant mode, int ordinal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ordinal + 1);
        m.put("method_name", mode.name());
        m.put("method_fields", List.of());
        m.put("is_default", ordinal == 0);
        m.put("is_active", true);
        m.put("created_at", null);
        m.put("updated_at", null);
        return m;
    }

    public List<Map<String, Object>> withdrawMethodList() {
        ModeVersementRestaurant[] values = ModeVersementRestaurant.values();
        return java.util.stream.IntStream.range(0, values.length)
                .mapToObj(i -> withdrawMethodRow(values[i], i))
                .collect(Collectors.toList());
    }

    /** Réponse bénigne {@code {message}} pour les mutations STUB (l'app ne lit que le statusCode). */
    public Map<String, Object> success(String message) {
        return Map.of("message", message);
    }

    /**
     * Objet "refund details" bénin, tous les champs numériques à {@code 0} non nul (jamais
     * {@code null} — contrainte Dart ci-dessus). STUB: no refund domain natively.
     */
    public Map<String, Object> refundDetailsStub() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("product_price", 0);
        m.put("quntity", 0);
        m.put("product_total_discount", 0);
        m.put("product_total_tax", 0);
        m.put("subtotal", 0);
        m.put("coupon_discount", 0);
        m.put("refund_amount", 0);
        m.put("refund_request", List.of());
        m.put("deliveryman_details", null);
        return m;
    }
}
