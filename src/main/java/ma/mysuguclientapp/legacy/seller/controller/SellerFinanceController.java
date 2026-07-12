package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.caisse.PaiementRestaurantDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.SellerFinanceMapper;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.implementations.FacturationRestaurantServiceImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finances du shim vendeur (contrat 6valley) : {@code /api/v3/seller/transactions},
 * {@code /withdraw-method-list}, {@code /balance-withdraw}, {@code /close-withdraw-request} et
 * {@code /refund/*}. Tranche 3f — GAP-lourde : mysugu n'a NI wallet vendeur natif, NI retrait
 * vendeur, NI domaine de remboursement (spec 3f §2 SCOPE DECISION). Seul {@code transactions}
 * s'appuie sur une donnée réelle (historique des paiements plateforme -> restaurant,
 * {@link FacturationRestaurantServiceImpl#getHistoriquePaiements}) ; tout le reste est un STUB
 * bénin explicitement documenté (jamais 404/500).
 * Voir docs/superpowers/specs/2026-07-10-vendor-3f-finances-design.md.
 */
@RestController
@RequestMapping("/api/v3/seller")
@RequiredArgsConstructor
public class SellerFinanceController {

    private final SellerContext sellerContext;
    private final RestaurantRepository restaurantRepository;
    private final FacturationRestaurantServiceImpl facturationService;
    private final SellerFinanceMapper mapper;

    /**
     * GET transactions(?status=&from=&to=) : historique des paiements plateforme -> restaurant du
     * vendeur authentifié, réponse = TABLEAU JSON NU (pas une enveloppe — voir
     * {@link SellerFinanceMapper} pour la déviation 3f.0 confirmée contre
     * {@code transaction_controller.dart}). {@code status}/{@code from}/{@code to} acceptés mais
     * non filtrants (aucune notion de statut 6valley côté {@code PaiementRestaurant} — spec 3f
     * §4) : jamais 500. Propriétaire sans restaurant ou sans paiement -> tableau vide (jamais
     * 404 — spec 3f §4/tâche 3f.1).
     */
    @GetMapping("/transactions")
    public List<Map<String, Object>> transactions(@AuthenticationPrincipal String email,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String from,
                                                   @RequestParam(required = false) String to) {
        User owner = sellerContext.requireOwner(email);
        Optional<Restaurant> restaurant = restaurantRepository.findByOwnerId(owner.getId());
        if (restaurant.isEmpty()) {
            return List.of();
        }
        Long restaurantId = restaurant.get().getId();
        Page<PaiementRestaurantDTO> page = facturationService.getHistoriquePaiements(restaurantId, Pageable.unpaged());
        return mapper.transactionList(page.getContent(), restaurantId);
    }

    /**
     * GET withdraw-method-list : STUB bénin, une ligne par valeur de l'enum natif
     * {@link ma.mysuguclientapp.enumerations.ModeVersementRestaurant} (pas de méthodes de retrait
     * configurables réellement — spec 3f §3 GAP). Réponse = TABLEAU JSON NU (confirmé 3f.0 contre
     * {@code wallet_controller.dart::getWithdrawMethods}). Jamais 500.
     * STUB: no native seller wallet/withdraw/refund (spec 3f SCOPE DECISION, umbrella §4).
     */
    @GetMapping("/withdraw-method-list")
    public List<Map<String, Object>> withdrawMethodList(@AuthenticationPrincipal String email) {
        sellerContext.requireOwner(email);
        return mapper.withdrawMethodList();
    }

    /**
     * POST balance-withdraw {amount, withdraw_method_id, ...} : STUB bénin succès-no-op (pas de
     * solde/retrait vendeur natif — spec 3f §2 SCOPE DECISION). Aucune persistance (aucune entité
     * "demande de retrait vendeur" n'existe côté natif). Jamais 500.
     * STUB: no native seller wallet/withdraw/refund (spec 3f SCOPE DECISION, umbrella §4). Needs
     * product-owner sign-off for a real build.
     */
    @PostMapping("/balance-withdraw")
    public Map<String, Object> balanceWithdraw(@AuthenticationPrincipal String email,
                                               @RequestBody(required = false) Map<String, Object> body) {
        sellerContext.requireOwner(email);
        return mapper.success("Demande de retrait envoyée.");
    }

    /**
     * POST close-withdraw-request : STUB bénin succès-no-op. URL réelle confirmée 3f.0 contre
     * {@code app_constants.dart} ({@code cancelBalanceRequest}) — SANS suffixe {@code {id}}
     * (déviation par rapport à {@code /withdraw/close-request/{id}} supposé par la conception
     * initiale de la spec ; route définie mais non appelée par l'app actuelle). Jamais 500.
     * STUB: no native seller wallet/withdraw/refund (spec 3f SCOPE DECISION, umbrella §4). Needs
     * product-owner sign-off for a real build.
     */
    @PostMapping("/close-withdraw-request")
    public Map<String, Object> closeWithdrawRequest(@AuthenticationPrincipal String email,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        sellerContext.requireOwner(email);
        return mapper.success("Demande de retrait annulée.");
    }
}
