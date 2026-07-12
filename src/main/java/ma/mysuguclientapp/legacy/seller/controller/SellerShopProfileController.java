package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.LocationUpdateDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.dtos.UserUpdateDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.SellerProfileMapper;
import ma.mysuguclientapp.legacy.seller.mapper.ShopMapper;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
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
    private final RestaurantService restaurantService;
    private final SellerProfileMapper sellerProfileMapper;
    private final ShopMapper shopMapper;

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

    /** GET shop-info : boutique du vendeur (son Restaurant), forme 6valley. */
    @GetMapping("/shop-info")
    public Map<String, Object> shopInfo(@AuthenticationPrincipal String email) {
        sellerContext.currentRestaurant(email); // 403 non-vendeur / 404 sans restaurant
        RestaurantDTO resto = restaurantService.getMonRestaurant(email);
        return shopMapper.toShopInfo(resto);
    }

    /**
     * POST shop-update (multipart, {@code _method:put} toléré) : met à jour la boutique du
     * vendeur. L'id du Restaurant est TOUJOURS résolu via {@link SellerContext#currentRestaurant}
     * (jamais depuis le corps) — pas d'écriture cross-tenant. Champs 6valley sans équivalent natif
     * (bannières, minimum_order_amount, delivery_charge, free_delivery) acceptés et ignorés.
     */
    @PostMapping(value = "/shop-update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> shopUpdate(
            @AuthenticationPrincipal String email,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "delivery_time", required = false) Integer deliveryTime,
            @RequestParam(value = "logo", required = false) MultipartFile logo) {
        Long restaurantId = sellerContext.currentRestaurant(email).getId(); // id serveur, jamais du corps
        RestaurantDTO current = restaurantService.getMonRestaurant(email);
        RestaurantDTO updated = restaurantService.updateRestaurant(restaurantId,
                shopMapper.toRestaurantUpdate(current, name, address, deliveryTime), logo);
        return shopMapper.toShopInfo(updated);
    }

    /**
     * POST temporary-close (_method:put) {status} : fermeture temporaire de la boutique.
     * L'app envoie {@code status=1} pour fermer, {@code status=0} pour rouvrir.
     * // GAP: mapped to Restaurant.isActive (umbrella §4). isActive = (status != 1).
     * Enveloppe 6valley bénigne — jamais 404/500 (hors 404 légitime "aucun restaurant").
     */
    @PostMapping("/temporary-close")
    public Map<String, Object> temporaryClose(@AuthenticationPrincipal String email,
                                              @RequestBody(required = false) Map<String, Object> body) {
        Restaurant resto = sellerContext.currentRestaurant(email);
        int status = intVal(body != null ? body.get("status") : null); // 1 = fermer, 0 = rouvrir
        boolean desiredActive = status != 1;
        if (!Boolean.valueOf(desiredActive).equals(resto.getIsActive())) {
            restaurantService.toggleRestaurantStatus(resto.getId()); // set isActive via toggle natif
        }
        return Map.of(
                "message", "Statut de la boutique mis à jour.",
                "temporary_close", !desiredActive);
    }

    private static int intVal(Object o) {
        if (o == null) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
