package ma.mysuguclientapp;

import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.entities.CategorieRestaurant;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.TypeCommission;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.CategoriesRestaurantRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.TokenVerificationRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.EmailService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Création d'un établissement depuis le back-office, rejouée en vrai HTTP multipart.
 *
 * <p>Le défaut d'origine n'était pas dans le service mais dans la liaison de la requête :
 * l'assistant postait {@code adresse}, {@code latitude}, {@code commissionPourcentage}, et
 * {@code @ModelAttribute} ignorait en silence tout champ absent du DTO. L'établissement était
 * bien créé — sans adresse, sans coordonnées, sans commission — donc hors de toute recherche
 * par zone côté client. Un test au niveau service serait passé malgré le défaut : il faut
 * passer par HTTP pour l'attraper.</p>
 *
 * <p>Prérequis : le Postgres de test du projet (conteneur {@code mysugu-test-db} sur :5433),
 * comme les autres tests d'intégration.</p>
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreationEtablissementBackOfficeTest {

    @LocalServerPort int port;
    @Autowired JwtTokenProvider jwt;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired CategoriesRestaurantRepository categorieRepository;
    @Autowired TokenVerificationRepository tokenVerificationRepository;
    @Autowired PasswordEncoder encoder;
    @Autowired TransactionTemplate tx;
    @MockitoBean EmailService emailService;

    private final String suffixe = String.valueOf(System.nanoTime());
    private final String adminEmail = "admin-bo-" + suffixe + "@test.mysugu";
    private final String vendeurEmail = "vendeur-bo-" + suffixe + "@test.mysugu";
    private final String restaurateurEmail = "resto-bo-" + suffixe + "@test.mysugu";
    private final String epicierEmail = "alim-bo-" + suffixe + "@test.mysugu";
    private final String plateEmail = "plat-bo-" + suffixe + "@test.mysugu";
    private final String majEmail = "maj-bo-" + suffixe + "@test.mysugu";

    private String token;
    private Long categorieCosmetiqueId;
    private Long categorieRestaurantId;

    @BeforeAll
    void setup() {
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setNom("Admin");
        admin.setPrenom("BO");
        admin.setPassword(encoder.encode("password123"));
        admin.setRole(UserRole.ADMIN);
        admin.setIsActive(true);
        token = jwt.generateToken(userRepository.save(admin));

        CategorieRestaurant cosmetique = new CategorieRestaurant();
        cosmetique.setNom("Soins visage " + suffixe);
        cosmetique.setVertical(Vertical.COSMETIQUE);
        categorieCosmetiqueId = categorieRepository.save(cosmetique).getId();

        // Verticale laissée à null : c'est ainsi que sont stockées les catégories historiques.
        CategorieRestaurant resto = new CategorieRestaurant();
        resto.setNom("Marocain " + suffixe);
        categorieRestaurantId = categorieRepository.save(resto).getId();
    }

    @AfterAll
    void cleanup() {
        tx.executeWithoutResult(s -> restaurantRepository.findAll().stream()
                .filter(r -> r.getNom() != null && r.getNom().endsWith(suffixe))
                .forEach(restaurantRepository::delete));

        tx.executeWithoutResult(s -> List.of(vendeurEmail, restaurateurEmail, epicierEmail,
                        plateEmail, majEmail, adminEmail)
                .forEach(email -> userRepository.findByEmail(email).ifPresent(u -> {
                    tokenVerificationRepository.findAll().stream()
                            .filter(t -> t.getUser() != null && u.getId().equals(t.getUser().getId()))
                            .forEach(tokenVerificationRepository::delete);
                    userRepository.delete(u);
                })));

        tx.executeWithoutResult(s -> categorieRepository
                .findAllById(List.of(categorieCosmetiqueId, categorieRestaurantId))
                .forEach(categorieRepository::delete));
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Boutique de cosmétique + vendeur : la fiche postée par l'assistant est enregistrée entière")
    void creationBoutiqueCosmetiqueAvecVendeur() {
        Map<String, String> form = formulaireAssistant("Boutique Eclat " + suffixe);
        form.put("vertical", "COSMETIQUE");
        form.put("categorieId", categorieCosmetiqueId.toString());
        form.put("ownerEmail", vendeurEmail);
        form.put("ownerNom", "Diallo");
        form.put("ownerPrenom", "Aminata");

        assertThat(poster(form)).isEqualTo(201);

        Restaurant boutique = parNom("Boutique Eclat " + suffixe);
        assertThat(boutique.getVertical()).isEqualTo(Vertical.COSMETIQUE);

        // Le coeur du défaut : ces champs partaient à plat et n'étaient liés à rien.
        assertThat(boutique.getLocalisation()).isNotNull();
        assertThat(boutique.getLocalisation().getAdresse()).isEqualTo("12 Rue des Jardins");
        assertThat(boutique.getLocalisation().getVille()).isEqualTo("Dakar");
        assertThat(boutique.getLocalisation().getLatitude()).isEqualTo(14.716677);
        assertThat(boutique.getLocalisation().getLongitude()).isEqualTo(-17.466675);

        // Le vendeur est provisionné avec le bon rôle, sans mot de passe transmis en clair.
        User vendeur = userRepository.findByEmail(vendeurEmail).orElseThrow();
        assertThat(vendeur.getRole()).isEqualTo(UserRole.RESTAURANT_OWNER);
        assertThat(vendeur.getNom()).isEqualTo("Diallo");
        assertThat(boutique.getOwner().getId()).isEqualTo(vendeur.getId());
    }

    @Test
    @DisplayName("Restaurant + restaurateur : la commission saisie à l'assistant est bien celle enregistrée")
    void creationRestaurantAvecCommissionPourcentage() {
        Map<String, String> form = formulaireAssistant("Chez Fatou " + suffixe);
        form.put("vertical", "RESTAURANT");
        form.put("categorieId", categorieRestaurantId.toString());
        form.put("ownerEmail", restaurateurEmail);
        form.put("commissionType", "POURCENTAGE");
        form.put("commissionPourcentage", "12.5");

        assertThat(poster(form)).isEqualTo(201);

        Restaurant resto = parNom("Chez Fatou " + suffixe);
        assertThat(resto.getCommissionType()).isEqualTo(TypeCommission.POURCENTAGE);
        assertThat(resto.getCommissionPourcentage()).isEqualByComparingTo("12.5");
        assertThat(resto.getCommissionMontantFixe()).isNull();
    }

    @Test
    @DisplayName("Une commission fixe se pose dès la création et exclut le pourcentage")
    void creationAvecCommissionFixe() {
        Map<String, String> form = formulaireAssistant("Alimentation Sandaga " + suffixe);
        form.put("vertical", "ALIMENTAIRE");
        form.put("categorieId", categorieRestaurantId.toString());
        form.put("ownerEmail", epicierEmail);
        form.put("commissionType", "FIXE");
        form.put("commissionMontantFixe", "150");

        assertThat(poster(form)).isEqualTo(201);

        Restaurant boutique = parNom("Alimentation Sandaga " + suffixe);
        assertThat(boutique.getCommissionType()).isEqualTo(TypeCommission.FIXE);
        assertThat(boutique.getCommissionMontantFixe()).isEqualByComparingTo("150");
        assertThat(boutique.getCommissionPourcentage()).isNull();
    }

    @Test
    @DisplayName("Les champs d'adresse postés à plat sont acceptés au même titre que la forme imbriquée")
    void adressePosteeAPlatEstAcceptee() {
        // Forme que postait l'assistant du back-office, et que le DTO ignorait en silence.
        // Ce cas échoue tant que RestaurantCreateDTO n'expose pas les alias à plat.
        Map<String, String> form = formulaireAssistant("Boutique A Plat " + suffixe);
        form.remove("localisation.adresse");
        form.remove("localisation.ville");
        form.remove("localisation.latitude");
        form.remove("localisation.longitude");
        form.put("adresse", "5 Avenue Bourguiba");
        form.put("ville", "Bamako");
        form.put("latitude", "12.639232");
        form.put("longitude", "-8.002889");
        form.put("vertical", "ALIMENTAIRE");
        form.put("categorieId", categorieRestaurantId.toString());
        form.put("ownerEmail", plateEmail);

        assertThat(poster(form)).isEqualTo(201);

        Restaurant boutique = parNom("Boutique A Plat " + suffixe);
        assertThat(boutique.getLocalisation()).isNotNull();
        assertThat(boutique.getLocalisation().getAdresse()).isEqualTo("5 Avenue Bourguiba");
        assertThat(boutique.getLocalisation().getVille()).isEqualTo("Bamako");
        assertThat(boutique.getLocalisation().getLatitude()).isEqualTo(12.639232);
        assertThat(boutique.getLocalisation().getLongitude()).isEqualTo(-8.002889);
    }

    @Test
    @DisplayName("Une mise à jour partielle de l'adresse n'efface pas les coordonnées connues")
    void miseAJourPartielleNeVidePasLesCoordonnees() {
        Map<String, String> creation = formulaireAssistant("Chez Awa " + suffixe);
        creation.put("vertical", "RESTAURANT");
        creation.put("categorieId", categorieRestaurantId.toString());
        creation.put("ownerEmail", majEmail);
        assertThat(poster(creation)).isEqualTo(201);
        Long id = parNom("Chez Awa " + suffixe).getId();

        // Ce que poste la fiche d'édition quand l'utilisateur corrige l'adresse sans toucher
        // aux coordonnées : les champs latitude/longitude ne sont tout simplement pas envoyés.
        Map<String, String> maj = new LinkedHashMap<>();
        maj.put("nom", "Chez Awa " + suffixe);
        maj.put("localisation.adresse", "Nouvelle adresse");
        maj.put("localisation.ville", "Dakar");

        assertThat(mettreAJour(id, maj)).isEqualTo(200);

        Restaurant resto = parNom("Chez Awa " + suffixe);
        assertThat(resto.getLocalisation().getAdresse()).isEqualTo("Nouvelle adresse");
        // Écrasées à null, elles sortaient l'établissement des recherches par zone sans
        // que personne ne touche jamais au champ concerné.
        assertThat(resto.getLocalisation().getLatitude()).isEqualTo(14.716677);
        assertThat(resto.getLocalisation().getLongitude()).isEqualTo(-17.466675);
    }

    @Test
    @DisplayName("Les catégories d'une verticale ne débordent pas sur une autre")
    void categoriesFiltreesParVerticale() {
        List<String> cosmetiques = categorieRepository.findByVerticalEffectif(Vertical.COSMETIQUE)
                .stream().map(CategorieRestaurant::getNom).toList();
        List<String> restaurants = categorieRepository.findByVerticalEffectif(Vertical.RESTAURANT)
                .stream().map(CategorieRestaurant::getNom).toList();

        assertThat(cosmetiques).contains("Soins visage " + suffixe)
                .doesNotContain("Marocain " + suffixe);
        // La catégorie à vertical NULL reste une catégorie de restaurant.
        assertThat(restaurants).contains("Marocain " + suffixe)
                .doesNotContain("Soins visage " + suffixe);
    }

    // ─── outillage ───────────────────────────────────────────────────────────

    /** Les champs exactement tels que l'assistant du back-office les poste. */
    private Map<String, String> formulaireAssistant(String nom) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("nom", nom);
        form.put("description", "Cree par le test d'integration");
        form.put("localisation.adresse", "12 Rue des Jardins");
        form.put("localisation.ville", "Dakar");
        form.put("localisation.latitude", "14.716677");
        form.put("localisation.longitude", "-17.466675");
        form.put("heureOuverture", "09:00");
        form.put("heureFermeture", "23:00");
        form.put("tempsLivraisonMoyen", "35");
        form.put("ownerNom", "Test");
        form.put("ownerPrenom", "Proprio");
        form.put("ownerTel", "+221770000000");
        form.put("ownerSendInvite", "true");
        return form;
    }

    private int poster(Map<String, String> form) {
        return envoyer("POST", "/api/restaurants", form);
    }

    /**
     * Requête multipart écrite à la main : c'est exactement ce que produit un FormData de
     * navigateur, et cela évite d'introduire un client HTTP de test supplémentaire.
     */
    private int envoyer(String methode, String chemin, Map<String, String> form) {
        String saut = "\r\n";
        String frontiere = "----mysugu" + System.nanoTime();
        StringBuilder corps = new StringBuilder();
        form.forEach((champ, valeur) -> corps
                .append("--").append(frontiere).append(saut)
                .append("Content-Disposition: form-data; name=\"").append(champ).append("\"")
                .append(saut).append(saut)
                .append(valeur).append(saut));
        corps.append("--").append(frontiere).append("--").append(saut);

        try {
            HttpResponse<String> reponse = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                            .header("Content-Type", "multipart/form-data; boundary=" + frontiere)
                            .header("Authorization", "Bearer " + token)
                            .method(methode, HttpRequest.BodyPublishers.ofString(
                                    corps.toString(), StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() >= 400) {
                throw new AssertionError(methode + " " + chemin + " a échoué ("
                        + reponse.statusCode() + ") : " + reponse.body());
            }
            return reponse.statusCode();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int mettreAJour(Long id, Map<String, String> form) {
        return envoyer("PUT", "/api/restaurants/" + id, form);
    }

    private Restaurant parNom(String nom) {
        return restaurantRepository.findAll().stream()
                .filter(r -> nom.equals(r.getNom()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Etablissement non cree : " + nom));
    }
}
