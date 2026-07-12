package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.commerce.CodePromoCreateDTO;
import ma.mysuguclientapp.dtos.commerce.CodePromoDTO;
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
}
