package ma.mysuguclientapp.legacy.seller.mapper;

import ma.mysuguclientapp.dtos.LocalisationDTO;
import ma.mysuguclientapp.dtos.RestaurantCreateDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduit {@link RestaurantDTO} (natif) <-> la forme "shop" consommée par
 * Tiktak-vendor-app-moso (lib/features/shop/domain/models/shop_model.dart) derrière le contrat
 * 6valley {@code /api/v3/seller/shop-info} / {@code shop-update}. Simple translation de forme.
 *
 * <p>Le parseur {@code ShopModel.fromJson} de l'app lit {@code json['rating'].toDouble()} SANS
 * garde de nullité : on renvoie donc toujours {@code rating}/{@code rating_count} non-null, plus
 * des valeurs bénignes pour les champs 6valley sans équivalent natif (bannières, minimum_order,
 * delivery_charge) afin que l'écran boutique s'affiche sans planter.</p>
 */
@Component
public class ShopMapper {

    /** RestaurantDTO natif -> objet "shop" 6valley. {@code temporary_close = !isActive}. */
    public Map<String, Object> toShopInfo(RestaurantDTO r) {
        boolean active = Boolean.TRUE.equals(r.getIsActive());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getNom());
        m.put("address", r.getLocalisation() != null ? r.getLocalisation().getAdresse() : null);
        // 6valley n'a pas de "contact" boutique distinct : on expose l'email du propriétaire.
        m.put("contact", r.getOwnerEmail());
        m.put("image", r.getLogoUrl());
        m.put("created_at", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        m.put("updated_at", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        // GAP: bannières 6valley sans équivalent natif -> null bénin (l'app tolère l'absence d'image).
        m.put("banner", null);
        m.put("bottom_banner", null);
        m.put("offer_banner", null);
        m.put("rating", r.getAppreciation() != null ? r.getAppreciation() : 0.0);
        m.put("rating_count", r.getNombreAvis() != null ? r.getNombreAvis() : 0);
        // GAP: temporary_close mappé sur Restaurant.isActive (umbrella §4).
        m.put("temporary_close", !active);
        // GAP: pas de modèle vacances natif -> échos bénins (umbrella §4/§7).
        m.put("vacation_status", false);
        m.put("vacation_start_date", null);
        m.put("vacation_end_date", null);
        // GAP: champs boutique 6valley sans équivalent natif -> défauts bénins.
        m.put("minimum_order_amount", 0);
        m.put("delivery_charge", 0);
        m.put("free_delivery_status", 0);
        return m;
    }

    /**
     * Formulaire 6valley shop-update ({@code name,contact,address,delivery_time}) ->
     * RestaurantCreateDTO natif. Reporte les champs inchangés issus du RestaurantDTO courant
     * pour ne pas écraser {@code description}/{@code vertical}/{@code categorie} avec des nulls.
     * {@code ownerId} laissé null : {@code updateRestaurant} conserve alors le propriétaire actuel.
     */
    public RestaurantCreateDTO toRestaurantUpdate(RestaurantDTO current, String name, String address,
                                                  Integer deliveryTime) {
        RestaurantCreateDTO dto = new RestaurantCreateDTO();
        dto.setNom(name != null && !name.isBlank() ? name : current.getNom());
        // Champs conservés pour éviter de les remettre à null via updateRestaurant.
        dto.setDescription(current.getDescription());
        dto.setVertical(current.getVertical());
        dto.setCategorieId(current.getCategorie() != null ? current.getCategorie().getId() : null);
        dto.setTempsLivraisonMoyen(deliveryTime != null ? deliveryTime : current.getTempsLivraisonMoyen());
        dto.setHeureOuverture(current.getHeureOuverture());
        dto.setHeureFermeture(current.getHeureFermeture());
        dto.setAutoCloseEnabled(current.getAutoCloseEnabled());
        dto.setOwnerId(null); // ne jamais réattribuer le propriétaire depuis le shim

        LocalisationDTO loc = current.getLocalisation() != null ? current.getLocalisation() : new LocalisationDTO();
        if (address != null && !address.isBlank()) {
            loc.setAdresse(address);
        }
        dto.setLocalisation(loc);
        return dto;
    }
}
