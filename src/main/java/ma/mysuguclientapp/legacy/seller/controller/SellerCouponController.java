package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.commerce.AppliquerCodePromoDTO;
import ma.mysuguclientapp.dtos.commerce.CodePromoCreateDTO;
import ma.mysuguclientapp.dtos.commerce.CodePromoDTO;
import ma.mysuguclientapp.dtos.commerce.ResultatCodePromoDTO;
import ma.mysuguclientapp.entities.CodePromo;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.SellerCouponMapper;
import ma.mysuguclientapp.repositories.CodePromoRepository;
import ma.mysuguclientapp.services.interfaces.CodePromoService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Coupons du shim vendeur (contrat 6valley) : {@code /api/v3/seller/coupon/*} réutilise
 * {@link CodePromoService} en scopant systématiquement au vendeur authentifié via {@code createdBy}
 * (CodePromo n'a pas de FK restaurant — scoping par créateur, spec §3). Un coupon appartenant à un
 * AUTRE vendeur -> 404 (jamais de fuite/mutation cross-vendeur) : garde
 * {@link #requireOwnedCoupon}. Voir docs/superpowers/specs/2026-07-10-vendor-3h-coupons-design.md.
 */
@RestController
@RequestMapping("/api/v3/seller/coupon")
@RequiredArgsConstructor
@Slf4j
public class SellerCouponController {

    private final SellerContext sellerContext;
    private final CodePromoService codePromoService;
    private final CodePromoRepository codePromoRepository;
    private final SellerCouponMapper mapper;

    /**
     * POST coupon/store : crée un coupon sous le vendeur authentifié. {@code createdBy = owner} est
     * TOUJOURS forcé côté serveur (native creerCodePromo laisse createdBy null — §4) pour que le
     * filtrage par propriétaire fonctionne. Renvoie le JSON coupon 6valley.
     */
    @PostMapping("/store")
    public Map<String, Object> store(@AuthenticationPrincipal String email,
                                     @RequestBody Map<String, Object> form) {
        User owner = sellerContext.requireOwner(email);
        CodePromoCreateDTO dto = mapper.toCreateDTO(form);
        CodePromoDTO created = codePromoService.creerCodePromo(dto);
        // createdBy non posé par le natif : on le pose dans le shim (§4) pour le scoping.
        CodePromo promo = codePromoRepository.findById(created.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Coupon introuvable après création"));
        promo.setCreatedBy(owner);
        CodePromo saved = codePromoRepository.save(promo);
        return mapper.toSixValley(codePromoService.getCodePromo(saved.getId()));
    }

    /**
     * GET coupon/list?limit&offset : coupons du vendeur authentifié (filtre createdBy = owner),
     * enveloppe de pagination 6valley {total_size, limit, offset, coupons:[...]}.
     */
    @GetMapping("/list")
    public Map<String, Object> list(@AuthenticationPrincipal String email,
                                    @RequestParam(defaultValue = "10") int limit,
                                    @RequestParam(defaultValue = "0") int offset) {
        User owner = sellerContext.requireOwner(email);
        List<CodePromo> all = codePromoRepository.findByCreatedById(owner.getId());
        int total = all.size();
        int safeLimit = Math.max(limit, 0);
        int from = Math.min(Math.max(offset, 0), total);
        int to = safeLimit == 0 ? from : Math.min(from + safeLimit, total);
        List<Map<String, Object>> coupons = all.subList(from, to).stream()
                .map(promo -> mapper.toSixValley(codePromoService.getCodePromo(promo.getId())))
                .toList();
        return mapper.listEnvelope(coupons, total, limit, offset);
    }

    /**
     * POST coupon/update/{id} ({@code _method:put}) : édite un coupon du vendeur authentifié.
     * Appartenance TOUJOURS vérifiée via {@link #requireOwnedCoupon} avant écriture — coupon d'un
     * AUTRE vendeur -> 404 (jamais de mutation cross-vendeur). Délègue à
     * {@link CodePromoService#mettreAJour}.
     */
    @PostMapping({"/update/{id}", "/update/{id}/"})
    public Map<String, Object> update(@AuthenticationPrincipal String email,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, Object> form) {
        User owner = sellerContext.requireOwner(email);
        requireOwnedCoupon(id, owner); // 404 si le coupon n'appartient pas au vendeur
        CodePromoCreateDTO dto = mapper.toCreateDTO(form);
        codePromoService.mettreAJour(id, dto);
        return mapper.success("Coupon mis à jour.");
    }

    /**
     * POST coupon/status-update/{id} {status} : bascule isActive via
     * {@link CodePromoService#activerDesactiver}. {@code status=1} => actif, {@code 0} => inactif.
     * Appartenance TOUJOURS vérifiée avant écriture — coupon d'un AUTRE vendeur -> 404.
     */
    @PostMapping({"/status-update/{id}", "/status-update/{id}/"})
    public Map<String, Object> statusUpdate(@AuthenticationPrincipal String email,
                                            @PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        User owner = sellerContext.requireOwner(email);
        requireOwnedCoupon(id, owner); // 404 si le coupon n'appartient pas au vendeur
        int status = body != null && body.get("status") != null
                ? Integer.parseInt(body.get("status").toString().trim()) : 0;
        codePromoService.activerDesactiver(id, status == 1);
        return mapper.success("Statut du coupon mis à jour.");
    }

    /**
     * DELETE|POST coupon/delete/{id} ({@code _method:delete}) : supprime un coupon du vendeur
     * authentifié. Appartenance TOUJOURS vérifiée avant suppression — coupon d'un AUTRE vendeur
     * -> 404 (jamais de suppression cross-vendeur).
     */
    @RequestMapping(value = {"/delete/{id}", "/delete/{id}/"}, method = {RequestMethod.DELETE, RequestMethod.POST})
    public Map<String, Object> delete(@AuthenticationPrincipal String email, @PathVariable Long id) {
        User owner = sellerContext.requireOwner(email);
        requireOwnedCoupon(id, owner); // 404 si le coupon n'appartient pas au vendeur
        codePromoService.supprimerCodePromo(id);
        return mapper.success("Coupon supprimé.");
    }

    /**
     * GET|POST coupon/check-coupon : valide un code et calcule la remise en déléguant à
     * {@link CodePromoService#validerEtCalculer} (le natif ne connaît que POST /valider — le POS
     * 6valley POST {@code {code,user_id,order_amount}}, spec §2). Code invalide/expiré -> forme
     * bénigne (montant 0), jamais 500. {@code order_amount} défaut 0.
     */
    @RequestMapping(value = "/check-coupon", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> checkCoupon(@AuthenticationPrincipal String email,
                                           @RequestParam(value = "code", required = false) String codeParam,
                                           @RequestParam(value = "order_amount", required = false) java.math.BigDecimal orderAmountParam,
                                           @RequestBody(required = false) Map<String, Object> body) {
        sellerContext.requireOwner(email); // 403 si non-vendeur
        String code = codeParam;
        java.math.BigDecimal orderAmount = orderAmountParam;
        if (body != null) {
            if (code == null && body.get("code") != null) {
                code = body.get("code").toString();
            }
            if (orderAmount == null && body.get("order_amount") != null) {
                orderAmount = new java.math.BigDecimal(body.get("order_amount").toString().trim());
            }
        }
        if (orderAmount == null) {
            orderAmount = java.math.BigDecimal.ZERO;
        }
        if (code == null || code.isBlank()) {
            return mapper.toCheckResult(ResultatCodePromoDTO.builder()
                    .valide(false).message("Code promo invalide ou expiré")
                    .montantOriginal(orderAmount).montantFinal(orderAmount).build());
        }
        AppliquerCodePromoDTO dto = new AppliquerCodePromoDTO();
        dto.setCode(code);
        dto.setMontantCommande(orderAmount);
        ResultatCodePromoDTO res = codePromoService.validerEtCalculer(dto, null);
        return mapper.toCheckResult(res);
    }

    /**
     * GET coupon/customers?name= : STUB — mysugu n'a pas de ciblage client / coupons par client
     * (spec §7). Renvoie une liste vide bénigne {customers:[]} (jamais 404/500).
     * // STUB: no native customer targeting (umbrella §4 GAP handling; spec §7).
     */
    @GetMapping("/customers")
    public Map<String, Object> customers(@AuthenticationPrincipal String email,
                                         @RequestParam(value = "name", required = false) String name) {
        sellerContext.requireOwner(email); // 403 si non-vendeur
        return Map.of("customers", List.of());
    }

    /**
     * Charge le coupon par id et vérifie qu'il a été créé par le vendeur authentifié. Sinon 404
     * (jamais de fuite/mutation cross-vendeur) — garde réutilisée par update/status/delete
     * (miroir de {@code ownedOrder}/{@code ownedPlat} des tranches précédentes).
     */
    private CodePromo requireOwnedCoupon(Long id, User owner) {
        CodePromo promo = codePromoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon non trouvé"));
        if (promo.getCreatedBy() == null || !promo.getCreatedBy().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon non trouvé");
        }
        return promo;
    }
}
