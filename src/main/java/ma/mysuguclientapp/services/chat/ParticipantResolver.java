package ma.mysuguclientapp.services.chat;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ParticipantResolver {
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;

    /** User to push FCM to. RESTAURANT→owner; CUSTOMER/LIVREUR→self; ADMIN→null (no target). */
    public Long notifiableUserId(ParticipantRef ref) {
        if (ref == null || ref.id() == null) return null;
        return switch (ref.type()) {
            case CUSTOMER, LIVREUR -> ref.id();
            case RESTAURANT -> restaurantRepository.findById(ref.id())
                    .map(Restaurant::getOwner).map(User::getId).orElse(null);
            case ADMIN -> null;
        };
    }

    public Map<String,Object> userInfo(Long userId) {
        Map<String,Object> m = new LinkedHashMap<>();
        User u = userId != null ? userRepository.findById(userId).orElse(null) : null;
        String f = u != null && u.getPrenom() != null ? u.getPrenom() : "";
        String l = u != null && u.getNom() != null ? u.getNom() : "";
        m.put("id", u != null ? u.getId() : (userId != null ? userId : 0));
        m.put("f_name", f); m.put("l_name", l);
        m.put("name", (f + " " + l).trim());
        m.put("image", u != null && u.getAvatar() != null ? u.getAvatar() : "");
        m.put("phone", u != null && u.getTelephone() != null ? u.getTelephone() : "");
        m.put("email", u != null && u.getEmail() != null ? u.getEmail() : "");
        m.put("shops", List.of());
        return m;
    }

    public Map<String,Object> restaurantInfo(Long restaurantId) {
        Map<String,Object> m = new LinkedHashMap<>();
        Restaurant r = restaurantId != null ? restaurantRepository.findById(restaurantId).orElse(null) : null;
        m.put("id", r != null ? r.getId() : (restaurantId != null ? restaurantId : 0));
        m.put("f_name", r != null && r.getNom() != null ? r.getNom() : "");
        m.put("l_name", ""); m.put("name", r != null && r.getNom() != null ? r.getNom() : "");
        m.put("image", r != null && r.getLogoUrl() != null ? r.getLogoUrl() : "");
        Map<String,Object> shop = new LinkedHashMap<>();
        shop.put("name", r != null && r.getNom() != null ? r.getNom() : "");
        m.put("shops", List.of(shop));
        return m;
    }
}
