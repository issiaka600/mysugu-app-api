package ma.mysuguclientapp.legacy.seller.mapper;

import ma.mysuguclientapp.dtos.LocationUpdateDTO;
import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.dtos.UserUpdateDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduit {@link UserDTO} (natif, {@code UserService.getProfile}) <-> la forme "seller"
 * consommée par Tiktak-vendor-app-moso (lib/features/profile/domain/models/profile_info.dart
 * et profile_body.dart) derrière le contrat 6valley {@code /api/v3/seller/seller-info} /
 * {@code seller-update}. Simple translation de forme, aucune logique métier.
 */
@Component
public class SellerProfileMapper {

    /** UserDTO natif -> objet "seller" 6valley ({@code id,f_name,l_name,phone,email,image,status}). */
    public Map<String, Object> toSellerInfo(UserDTO u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("f_name", u.getPrenom());
        m.put("l_name", u.getNom());
        m.put("phone", u.getTelephone());
        m.put("email", u.getEmail());
        m.put("image", u.getAvatar());
        m.put("status", Boolean.TRUE.equals(u.getIsActive()) ? "active" : "inactive");
        // GAP: champs 6valley sans équivalent natif (POS, ventes) -> valeurs bénignes non-null.
        // ProfileInfoModel.fromJson (app vendeur) lit pos_status SANS garde de nullité :
        // un JSON sans cette clé ferait planter l'écran profil au parsing.
        m.put("pos_status", 0);
        m.put("product_count", 0);
        m.put("orders_count", 0);
        m.put("minimum_order_amount", 0);
        m.put("free_delivery_over_amount", 0);
        m.put("free_delivery_status", 0);
        return m;
    }

    /** Formulaire 6valley seller-update ({@code f_name,l_name,phone}) -> UserUpdateDTO natif. */
    public UserUpdateDTO toUserUpdate(String fName, String lName, String phone) {
        UserUpdateDTO dto = new UserUpdateDTO();
        if (hasText(fName)) {
            dto.setPrenom(fName);
        }
        if (hasText(lName)) {
            dto.setNom(lName);
        }
        if (hasText(phone)) {
            dto.setTelephone(phone);
        }
        return dto;
    }

    /** Bloc adresse optionnel du formulaire 6valley -> LocationUpdateDTO natif. */
    public LocationUpdateDTO toLocation(Double latitude, Double longitude, String adresse,
                                        String ville, String pays, String codePostal) {
        LocationUpdateDTO dto = new LocationUpdateDTO();
        dto.setLatitude(latitude);
        dto.setLongitude(longitude);
        dto.setAdresse(adresse);
        dto.setVille(ville);
        dto.setPays(pays);
        dto.setCodePostal(codePostal);
        return dto;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
