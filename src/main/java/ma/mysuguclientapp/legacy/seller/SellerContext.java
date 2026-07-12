package ma.mysuguclientapp.legacy.seller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Résolveur d'identité pour le shim vendeur (/api/v3/seller/*). Reproduit le rôle de
 * {@code SellerContext} décrit dans le design de la tranche 3a : à partir de l'email
 * authentifié (principal JWT), retrouve le {@link User} RESTAURANT_OWNER puis son
 * {@link Restaurant} (un seul restaurant par propriétaire). Utilisé par tous les
 * contrôleurs du shim vendeur pour garder la même logique d'autorisation.
 */
@Component
@RequiredArgsConstructor
public class SellerContext {

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;

    /** Résout l'utilisateur authentifié et vérifie qu'il est bien RESTAURANT_OWNER. */
    public User requireOwner(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié"));
        if (u.getRole() != UserRole.RESTAURANT_OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé au vendeur");
        }
        return u;
    }

    /** Restaurant du vendeur authentifié (404 si aucun restaurant rattaché). */
    public Restaurant currentRestaurant(String email) {
        User owner = requireOwner(email);
        return restaurantRepository.findByOwnerId(owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun restaurant"));
    }
}
