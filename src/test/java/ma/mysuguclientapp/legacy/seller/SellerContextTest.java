package ma.mysuguclientapp.legacy.seller;

import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SellerContext resolves the RESTAURANT_OWNER + its restaurant from an authenticated
 * email principal. Backbone helper reused by every /api/v3/seller/* controller.
 */
@SpringBootTest
class SellerContextTest {

    @Autowired SellerContext ctx;
    @Autowired UserRepository users;
    @Autowired RestaurantRepository restos;

    private final String ownerEmail = "seller-ctx-owner-" + System.nanoTime() + "@test.mysugu";
    private final String clientEmail = "seller-ctx-client-" + System.nanoTime() + "@test.mysugu";

    @AfterEach
    void cleanup() {
        users.findByEmail(ownerEmail).ifPresent(owner -> {
            restos.findByOwnerId(owner.getId()).ifPresent(restos::delete);
            users.delete(owner);
        });
        users.findByEmail(clientEmail).ifPresent(users::delete);
    }

    @Test
    void currentRestaurant_resolves_by_owner() {
        User owner = newUser(ownerEmail, UserRole.RESTAURANT_OWNER);
        Restaurant r = new Restaurant();
        r.setNom("Chez X");
        r.setOwner(owner);
        r.setIsActive(true);
        r = restos.save(r);

        assertThat(ctx.currentRestaurant(ownerEmail).getId()).isEqualTo(r.getId());
    }

    @Test
    void currentRestaurant_withMultipleRestaurants_picksActiveApproved_withoutThrowing() {
        // Régression bug A : un owner peut posséder plusieurs restaurants (données réelles).
        // findByOwnerId ne doit PAS lever NonUniqueResult (500) mais renvoyer le restaurant
        // "principal" = actif + APPROUVE + le plus récent.
        User owner = newUser(ownerEmail, UserRole.RESTAURANT_OWNER);
        Restaurant legacy = new Restaurant();
        legacy.setNom("Legacy (non approuvé)");
        legacy.setOwner(owner);
        legacy.setIsActive(true);
        legacy = restos.save(legacy);
        Restaurant principal = new Restaurant();
        principal.setNom("Principal approuvé");
        principal.setOwner(owner);
        principal.setIsActive(true);
        principal.setStatutApprobation(ma.mysuguclientapp.enumerations.StatutRestaurant.APPROUVE);
        principal = restos.save(principal);
        try {
            Restaurant resolved = ctx.currentRestaurant(ownerEmail);
            assertThat(resolved.getId()).isEqualTo(principal.getId());
        } finally {
            // Nettoyage des DEUX restaurants avant @AfterEach (sinon FK sur delete owner).
            restos.delete(legacy);
            restos.delete(principal);
        }
    }

    @Test
    void requireOwner_rejects_non_owner() {
        newUser(clientEmail, UserRole.CLIENT);

        assertThatThrownBy(() -> ctx.requireOwner(clientEmail))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void requireOwner_rejects_unknown_email() {
        assertThatThrownBy(() -> ctx.requireOwner("unknown-" + System.nanoTime() + "@test.mysugu"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void currentRestaurant_rejects_owner_without_restaurant() {
        newUser(ownerEmail, UserRole.RESTAURANT_OWNER);

        assertThatThrownBy(() -> ctx.currentRestaurant(ownerEmail))
                .isInstanceOf(ResponseStatusException.class);
    }

    private User newUser(String email, UserRole role) {
        User u = new User();
        u.setEmail(email);
        u.setPassword("x");
        u.setNom("Test");
        u.setPrenom("User");
        u.setRole(role);
        u.setIsActive(true);
        return users.save(u);
    }
}
