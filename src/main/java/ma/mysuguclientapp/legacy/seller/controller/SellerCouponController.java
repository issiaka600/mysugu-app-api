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
}
