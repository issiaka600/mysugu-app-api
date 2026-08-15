package ma.mysuguclientapp;

import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Réaffectation d'un établissement à un autre propriétaire.
 *
 * L'invariant défendu ici est celui dont dépend l'authentification de l'app vendeur :
 * un propriétaire, un établissement. {@code SellerContext.currentRestaurant} résout par
 * {@code findByOwnerId} ; deux établissements pour un même propriétaire rendraient cette
 * résolution ambiguë et serviraient au vendeur une boutique arbitraire.
 *
 * Chaque refus vérifie AUSSI que rien n'a bougé : un refus laissant l'établissement à
 * moitié transféré serait pire que l'absence de garde.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReaffectationEtablissementTest {

    @Autowired RestaurantService restaurantService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired UserRepository userRepository;
    @Autowired SellerContext sellerContext;
    @Autowired PasswordEncoder encoder;
    @Autowired TransactionTemplate tx;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> restoIds = new ArrayList<>();

    @AfterAll
    void cleanup() {
        tx.executeWithoutResult(s -> {
            restoIds.forEach(id -> restaurantRepository.findById(id).ifPresent(restaurantRepository::delete));
            userIds.forEach(id -> userRepository.findById(id).ifPresent(userRepository::delete));
        });
    }

    @Test
    void reaffecte_versUnProprietaireSansEtablissement() {
        User ancien = proprietaire("reaff-ancien");
        User nouveau = proprietaire("reaff-nouveau");
        Restaurant r = etablissement(ancien, Vertical.ALIMENTAIRE);

        restaurantService.reaffecterProprietaire(r.getId(), nouveau.getId());

        Restaurant relu = restaurantRepository.findById(r.getId()).orElseThrow();
        assertThat(relu.getOwner().getId()).isEqualTo(nouveau.getId());
    }

    @Test
    void refuse_siLaCiblePossedeDejaUnEtablissement() {
        User ancien = proprietaire("reaff-a");
        User occupe = proprietaire("reaff-occupe");
        Restaurant r = etablissement(ancien, Vertical.ALIMENTAIRE);
        etablissement(occupe, Vertical.COSMETIQUE);

        assertThatThrownBy(() -> restaurantService.reaffecterProprietaire(r.getId(), occupe.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("possède déjà");

        // Rien n'a bouge.
        assertThat(restaurantRepository.findById(r.getId()).orElseThrow().getOwner().getId())
                .isEqualTo(ancien.getId());
    }

    @Test
    void refuse_siLaCibleNEstPasProprietaire() {
        User ancien = proprietaire("reaff-b");
        User client = utilisateur("reaff-client", UserRole.CLIENT);
        Restaurant r = etablissement(ancien, Vertical.ALIMENTAIRE);

        assertThatThrownBy(() -> restaurantService.reaffecterProprietaire(r.getId(), client.getId()))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(restaurantRepository.findById(r.getId()).orElseThrow().getOwner().getId())
                .isEqualTo(ancien.getId());
    }

    /**
     * La garde ne doit pas être contournable : {@code updateRestaurant} accepte aussi un
     * {@code ownerId} et changeait de propriétaire sans aucun contrôle d'unicité — défaut
     * préexistant, découvert en implémentant ce lot.
     */
    @Test
    void laMiseAJourOrdinaireNePeutPasContournerLaGarde() {
        User ancien = proprietaire("reaff-maj-ancien");
        User occupe = proprietaire("reaff-maj-occupe");
        Restaurant r = etablissement(ancien, Vertical.ALIMENTAIRE);
        etablissement(occupe, Vertical.COSMETIQUE);

        ma.mysuguclientapp.dtos.RestaurantCreateDTO dto = new ma.mysuguclientapp.dtos.RestaurantCreateDTO();
        // Le nom est obligatoire et updateRestaurant l'ecrit SANS condition : un DTO nu le
        // passerait a null, et l'auto-flush precedant la requete de la garde ferait echouer
        // l'appel sur la contrainte NOT NULL avant meme que la garde ne statue. L'interface
        // envoie toujours le nom, on reproduit donc ce qu'elle envoie.
        dto.setNom(r.getNom());
        dto.setOwnerId(occupe.getId());

        assertThatThrownBy(() -> restaurantService.updateRestaurant(r.getId(), dto, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("possède déjà");

        assertThat(restaurantRepository.findById(r.getId()).orElseThrow().getOwner().getId())
                .isEqualTo(ancien.getId());
    }

    @Test
    void apresReaffectation_leNouveauVendeurVoitLEtablissementEtPasLAncien() {
        User ancien = proprietaire("reaff-av");
        User nouveau = proprietaire("reaff-nv");
        Restaurant r = etablissement(ancien, Vertical.ALIMENTAIRE);

        restaurantService.reaffecterProprietaire(r.getId(), nouveau.getId());

        assertThat(sellerContext.currentRestaurant(nouveau.getEmail()).getId()).isEqualTo(r.getId());
        assertThatThrownBy(() -> sellerContext.currentRestaurant(ancien.getEmail()))
                .isInstanceOf(ResponseStatusException.class);
    }

    private User proprietaire(String prefix) {
        return utilisateur(prefix, UserRole.RESTAURANT_OWNER);
    }

    private User utilisateur(String prefix, UserRole role) {
        User u = new User();
        u.setEmail(prefix + "-" + System.nanoTime() + "@test.mysugu");
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("N"); u.setPrenom("P");
        u.setRole(role);
        u.setIsActive(true);
        User saved = userRepository.save(u);
        userIds.add(saved.getId());
        return saved;
    }

    private Restaurant etablissement(User owner, Vertical vertical) {
        Restaurant r = new Restaurant();
        r.setNom("Reaff " + System.nanoTime());
        r.setIsActive(true);
        r.setVertical(vertical);
        r.setOwner(owner);
        Restaurant saved = restaurantRepository.save(r);
        restoIds.add(saved.getId());
        return saved;
    }
}
