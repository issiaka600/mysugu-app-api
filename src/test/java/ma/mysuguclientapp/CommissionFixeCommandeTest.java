package ma.mysuguclientapp;

import ma.mysuguclientapp.dtos.CommandeCreateDTO;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.LigneCommandeCreateDTO;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.ParametresCaisse;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.ModeReceptionCommande;
import ma.mysuguclientapp.enumerations.TypeCommission;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.ParametresCaisseRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Commissions en montant fixe, de la commande jusqu'au montant retenu.
 *
 * <p>Les deux barèmes qui portent sur les articles se règlent indépendamment : celui négocié
 * avec l'établissement (au-dessus du seuil de prix) et le barème minimum global (en dessous).
 * Chaque cas est vérifié dans les deux modes, sinon un calcul qui ignorerait le type et
 * retomberait toujours sur le pourcentage passerait inaperçu.</p>
 *
 * <p>Tests {@code @Transactional} : le rollback dispense de nettoyer les commandes et les
 * paramètres globaux modifiés, comme dans {@link ParcoursBoutiqueE2ETest}.</p>
 */
@SpringBootTest
class CommissionFixeCommandeTest {

    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PlatRepository platRepository;
    @Autowired UserRepository userRepository;
    @Autowired CommandeRepository commandeRepository;
    @Autowired ParametresCaisseRepository parametresCaisseRepository;
    @Autowired CommandeService commandeService;

    private static final BigDecimal SEUIL = new BigDecimal("10.00");

    @Test
    @Transactional
    @DisplayName("Commission fixe de l'établissement : prélevée par article, pas par ligne")
    void commissionFixeEtablissementParArticle() {
        reglerSeuil();
        Restaurant boutique = creerEtablissement(TypeCommission.FIXE, null, new BigDecimal("20.00"));
        Plat article = creerArticle(boutique, new BigDecimal("120.00"));

        Commande commande = commander(boutique, article, 3);

        // 3 articles à 120 = 360 de ligne. 20 par article ⇒ 60, et non 20.
        assertThat(commande.getMontantCommissionTotal()).isEqualByComparingTo("60.00");
        assertThat(commande.getLignesCommande()).singleElement().satisfies(ligne -> {
            assertThat(ligne.getCommissionType()).isEqualTo(TypeCommission.FIXE);
            assertThat(ligne.getCommissionMontantFixe()).isEqualByComparingTo("20.00");
            // Le pourcentage reste vide : afficher « com. 0 % » sur une commission fixe
            // laisserait croire que rien n'est prélevé.
            assertThat(ligne.getCommissionPourcentage()).isNull();
            assertThat(ligne.getMontantCommission()).isEqualByComparingTo("60.00");
        });
    }

    @Test
    @Transactional
    @DisplayName("Commission en pourcentage : comportement inchangé")
    void commissionPourcentageEtablissement() {
        reglerSeuil();
        Restaurant resto = creerEtablissement(TypeCommission.POURCENTAGE, new BigDecimal("15.00"), null);
        Plat plat = creerArticle(resto, new BigDecimal("120.00"));

        Commande commande = commander(resto, plat, 3);

        // 360 × 15 % = 54
        assertThat(commande.getMontantCommissionTotal()).isEqualByComparingTo("54.00");
        assertThat(commande.getLignesCommande()).singleElement().satisfies(ligne -> {
            assertThat(ligne.getCommissionType()).isEqualTo(TypeCommission.POURCENTAGE);
            assertThat(ligne.getCommissionPourcentage()).isEqualByComparingTo("15.00");
            assertThat(ligne.getCommissionMontantFixe()).isNull();
        });
    }

    @Test
    @Transactional
    @DisplayName("Un type de commission absent garde le calcul en pourcentage des données antérieures")
    void etablissementSansTypeResteEnPourcentage() {
        reglerSeuil();
        Restaurant resto = creerEtablissement(null, new BigDecimal("10.00"), null);
        Plat plat = creerArticle(resto, new BigDecimal("120.00"));

        Commande commande = commander(resto, plat, 2);

        assertThat(commande.getMontantCommissionTotal()).isEqualByComparingTo("24.00");
    }

    @Test
    @Transactional
    @DisplayName("Sous le seuil, c'est le barème global qui s'applique — fixe compris")
    void baremeGlobalFixeSousLeSeuil() {
        ParametresCaisse params = reglerSeuil();
        params.setCommissionMinType(TypeCommission.FIXE);
        params.setCommissionMinMontantFixe(new BigDecimal("2.00"));
        parametresCaisseRepository.save(params);

        // La commission de l'établissement doit rester ignorée sous le seuil.
        Restaurant boutique = creerEtablissement(TypeCommission.POURCENTAGE, new BigDecimal("15.00"), null);
        Plat articleBonMarche = creerArticle(boutique, new BigDecimal("8.00"));

        Commande commande = commander(boutique, articleBonMarche, 5);

        // 2 par article × 5 = 10, là où 15 % de 40 n'aurait rapporté que 6.
        assertThat(commande.getMontantCommissionTotal()).isEqualByComparingTo("10.00");
        assertThat(commande.getLignesCommande()).singleElement().satisfies(ligne ->
                assertThat(ligne.getCommissionType()).isEqualTo(TypeCommission.FIXE));
    }

    @Test
    @Transactional
    @DisplayName("Une commission fixe aberrante est plafonnée au montant de la ligne")
    void commissionFixeAberranteEstPlafonnee() {
        reglerSeuil();
        Restaurant boutique = creerEtablissement(TypeCommission.FIXE, null, new BigDecimal("10000.00"));
        Plat article = creerArticle(boutique, new BigDecimal("120.00"));

        Commande commande = commander(boutique, article, 1);

        // Sans plafond, le net vendeur deviendrait négatif.
        assertThat(commande.getMontantCommissionTotal()).isEqualByComparingTo("120.00");
    }

    // ─── outillage ───────────────────────────────────────────────────────────

    /** Fige le seuil global pour que les tests ne dépendent pas du paramétrage de la base. */
    private ParametresCaisse reglerSeuil() {
        ParametresCaisse params = parametresCaisseRepository.findById(1L)
                .orElseGet(() -> parametresCaisseRepository.save(new ParametresCaisse()));
        params.setSeuilPrixCommission(SEUIL);
        params.setCommissionMinType(TypeCommission.POURCENTAGE);
        params.setCommissionMinPourcentage(new BigDecimal("20.00"));
        return parametresCaisseRepository.save(params);
    }

    private Restaurant creerEtablissement(TypeCommission type, BigDecimal pourcentage, BigDecimal montantFixe) {
        Restaurant etablissement = new Restaurant();
        etablissement.setNom("Commission " + System.nanoTime());
        etablissement.setIsActive(true);
        etablissement.setVertical(Vertical.ALIMENTAIRE);
        etablissement.setCommissionType(type);
        etablissement.setCommissionPourcentage(pourcentage);
        etablissement.setCommissionMontantFixe(montantFixe);
        return restaurantRepository.save(etablissement);
    }

    private Plat creerArticle(Restaurant etablissement, BigDecimal prix) {
        Plat article = new Plat();
        article.setNom("Article " + System.nanoTime());
        article.setPrix(prix);
        article.setIsAvailable(true);
        article.setQuantiteStock(100);
        article.setRestaurant(etablissement);
        return platRepository.save(article);
    }

    private User creerClient() {
        User client = new User();
        client.setEmail("client.commission." + System.nanoTime() + "@mysugu.test");
        client.setPassword("motdepasse");
        client.setNom("Test");
        client.setPrenom("Client");
        client.setRole(UserRole.CLIENT);
        client.setIsActive(true);
        return userRepository.save(client);
    }

    private Commande commander(Restaurant etablissement, Plat article, int quantite) {
        var ligne = new LigneCommandeCreateDTO();
        ligne.setPlatId(article.getId());
        ligne.setQuantite(quantite);

        var dto = new CommandeCreateDTO();
        dto.setClientId(creerClient().getId());
        dto.setRestaurantId(etablissement.getId());
        dto.setLignes(List.of(ligne));
        // RETRAIT_SUR_PLACE : évite d'avoir à créer une zone de déploiement.
        dto.setModeReception(ModeReceptionCommande.RETRAIT_SUR_PLACE.name());
        dto.setMethodePaiement("ESPECES");

        CommandeDTO creee = commandeService.createCommande(dto);
        return commandeRepository.findById(creee.getId()).orElseThrow();
    }
}
