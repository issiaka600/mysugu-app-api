package ma.mysuguclientapp;

import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.dtos.auth.AdminUserUpdateDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.TokenVerificationRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gestion administrative des comptes propriétaires.
 *
 * Comme partout où `vertical` intervient, le jeu de données comporte un établissement
 * à {@code vertical = NULL} et chaque cas vérifie les deux sens.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminProprietairesTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired TokenVerificationRepository tokenVerificationRepository;
    @Autowired PasswordEncoder encoder;
    @Autowired TransactionTemplate tx;

    private Long proprioHistoriqueId;
    private Long proprioBoutiqueId;
    private Long restoHistoriqueId;
    private Long boutiqueId;
    private String emailHistorique;
    private String emailBoutique;

    @BeforeAll
    void setup() {
        emailHistorique = "proprio-histo-" + System.nanoTime() + "@test.mysugu";
        emailBoutique = "proprio-boutique-" + System.nanoTime() + "@test.mysugu";

        proprioHistoriqueId = nouveauProprietaire(emailHistorique);
        proprioBoutiqueId = nouveauProprietaire(emailBoutique);

        // Etablissement historique : vertical NON renseigne => NULL en base.
        Restaurant historique = new Restaurant();
        historique.setNom("Histo Proprio " + System.nanoTime());
        historique.setIsActive(true);
        historique.setOwner(userRepository.findById(proprioHistoriqueId).orElseThrow());
        restoHistoriqueId = restaurantRepository.save(historique).getId();

        Restaurant boutique = new Restaurant();
        boutique.setNom("Epicerie Proprio " + System.nanoTime());
        boutique.setIsActive(true);
        boutique.setVertical(Vertical.ALIMENTAIRE);
        boutique.setOwner(userRepository.findById(proprioBoutiqueId).orElseThrow());
        boutiqueId = restaurantRepository.save(boutique).getId();
    }

    @AfterAll
    void cleanup() {
        tx.executeWithoutResult(s -> {
            tokenVerificationRepository.findAll().stream()
                    .filter(t -> t.getUser() != null
                            && (t.getUser().getId().equals(proprioHistoriqueId)
                                || t.getUser().getId().equals(proprioBoutiqueId)))
                    .forEach(tokenVerificationRepository::delete);
            restaurantRepository.deleteById(restoHistoriqueId);
            restaurantRepository.deleteById(boutiqueId);
            userRepository.deleteById(proprioHistoriqueId);
            userRepository.deleteById(proprioBoutiqueId);
        });
    }

    @Test
    void listeLesProprietairesDUneVerticale_etGardeLesHistoriquesEnRestaurant() {
        Page<UserDTO> alimentaire =
                userService.getProprietairesParVerticale(Vertical.ALIMENTAIRE, null, Pageable.unpaged());
        Page<UserDTO> restaurants =
                userService.getProprietairesParVerticale(Vertical.RESTAURANT, null, Pageable.unpaged());

        assertThat(alimentaire.getContent()).extracting(UserDTO::getEmail)
                .contains(emailBoutique)
                .doesNotContain(emailHistorique);

        // Ce qui doit rester reste : un etablissement a vertical NULL est un RESTAURANT.
        assertThat(restaurants.getContent()).extracting(UserDTO::getEmail)
                .contains(emailHistorique)
                .doesNotContain(emailBoutique);
    }

    /** Le chemin avec recherche est une requête distincte : il doit être exercé pour de vrai. */
    @Test
    void laRechercheFiltreSurEmailNomEtPrenom() {
        Page<UserDTO> parEmail = userService.getProprietairesParVerticale(
                Vertical.ALIMENTAIRE, emailBoutique, Pageable.unpaged());
        assertThat(parEmail.getContent()).extracting(UserDTO::getEmail).contains(emailBoutique);

        Page<UserDTO> sansCorrespondance = userService.getProprietairesParVerticale(
                Vertical.ALIMENTAIRE, "zzz-aucune-correspondance-zzz", Pageable.unpaged());
        assertThat(sansCorrespondance.getContent()).isEmpty();
    }

    @Test
    void metAJourLesCoordonneesSansToucherAuRoleNiAuMotDePasse() {
        User avant = userRepository.findById(proprioBoutiqueId).orElseThrow();
        String motDePasseAvant = avant.getPassword();

        AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
        dto.setNom("Nouveau");
        dto.setTelephone("+22370000000");

        UserDTO maj = userService.mettreAJourUtilisateur(proprioBoutiqueId, dto);

        assertThat(maj.getNom()).isEqualTo("Nouveau");
        assertThat(maj.getTelephone()).isEqualTo("+22370000000");
        assertThat(maj.getRole()).isEqualTo(UserRole.RESTAURANT_OWNER.name());

        User relu = userRepository.findById(proprioBoutiqueId).orElseThrow();
        assertThat(relu.getPassword()).isEqualTo(motDePasseAvant);
        assertThat(relu.getEmail()).isEqualTo(emailBoutique);
        // Champ absent du DTO => champ inchange.
        assertThat(relu.getPrenom()).isEqualTo("P");
    }

    @Test
    void relanceInvitation_creeUnJetonNeufValide() {
        userService.relancerInvitation(proprioHistoriqueId);

        var jetons = tokenVerificationRepository.findAll().stream()
                .filter(t -> t.getUser() != null && t.getUser().getId().equals(proprioHistoriqueId))
                .toList();

        assertThat(jetons).isNotEmpty();
        assertThat(jetons.get(jetons.size() - 1).getExpiresAt())
                .isAfter(java.time.LocalDateTime.now());
    }

    @Test
    void relanceInvitation_refuseUnUtilisateurQuiNEstPasProprietaire() {
        Long clientId = tx.execute(s -> {
            User u = new User();
            u.setEmail("client-relance-" + System.nanoTime() + "@test.mysugu");
            u.setPassword(encoder.encode("demo1234"));
            u.setNom("N"); u.setPrenom("P");
            u.setRole(UserRole.CLIENT);
            u.setIsActive(true);
            return userRepository.save(u).getId();
        });

        assertThatThrownBy(() -> userService.relancerInvitation(clientId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        tx.executeWithoutResult(s -> userRepository.deleteById(clientId));
    }

    private Long nouveauProprietaire(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("N"); u.setPrenom("P");
        u.setRole(UserRole.RESTAURANT_OWNER);
        u.setIsActive(true);
        return userRepository.save(u).getId();
    }
}
