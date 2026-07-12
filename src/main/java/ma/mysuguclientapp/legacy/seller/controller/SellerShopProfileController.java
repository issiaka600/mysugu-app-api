package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.LocationUpdateDTO;
import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.dtos.UserUpdateDTO;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.SellerProfileMapper;
import ma.mysuguclientapp.services.interfaces.UserService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Shop / profil du shim vendeur (contrat 6valley) : {@code seller-info}/{@code seller-update}
 * réutilisent {@code UserService} (profil du RESTAURANT_OWNER) ; {@code shop-info}/
 * {@code shop-update} réutilisent {@code RestaurantService} (le restaurant du vendeur, résolu
 * via {@link SellerContext} — jamais depuis un id du corps de requête). {@code temporary-close}
 * et {@code vacation-add} sont des GAP mappés sur {@code Restaurant.isActive} (voir méthodes
 * ajoutées dans les tranches 3b.5/3b.6). Voir
 * docs/superpowers/specs/2026-07-10-vendor-3b-shop-profile-design.md.
 */
@RestController
@RequestMapping("/api/v3/seller")
@RequiredArgsConstructor
@Slf4j
public class SellerShopProfileController {

    private final SellerContext sellerContext;
    private final UserService userService;
    private final SellerProfileMapper sellerProfileMapper;

    /** GET seller-info : profil du vendeur authentifié, forme 6valley. */
    @GetMapping("/seller-info")
    public Map<String, Object> sellerInfo(@AuthenticationPrincipal String email,
                                          @RequestHeader("Authorization") String authorization) {
        sellerContext.requireOwner(email); // 403 si non-vendeur
        UserDTO profile = userService.getProfile(authorization);
        return sellerProfileMapper.toSellerInfo(profile);
    }

    /**
     * POST seller-update (multipart, {@code _method:put} toléré) : met à jour le profil du
     * vendeur (f_name/l_name/phone + image optionnelle) via UserService.updateProfile ; si un
     * bloc adresse est fourni, met à jour la localisation via updateLocation. Renvoie l'objet
     * seller à jour.
     */
    @PostMapping(value = "/seller-update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> sellerUpdate(
            @AuthenticationPrincipal String email,
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "f_name", required = false) String fName,
            @RequestParam(value = "l_name", required = false) String lName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "zip_code", required = false) String zipCode) {
        sellerContext.requireOwner(email); // 403 si non-vendeur

        UserUpdateDTO update = sellerProfileMapper.toUserUpdate(fName, lName, phone);
        UserDTO result = userService.updateProfile(authorization, update, image);

        // Bloc adresse optionnel : l'app peut envoyer une localisation.
        if (address != null || latitude != null || longitude != null) {
            LocationUpdateDTO loc = sellerProfileMapper.toLocation(latitude, longitude, address,
                    city, country, zipCode);
            result = userService.updateLocation(authorization, loc);
        }
        return sellerProfileMapper.toSellerInfo(result);
    }
}
