package ma.mysuguclientapp.config;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.enumerations.*;
import ma.mysuguclientapp.repositories.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "test.data.enabled", havingValue = "true")
public class TestDataInitializer implements CommandLineRunner {

    private static final String ADMIN_EMAIL = "admin.demo@mysuku.ma";
    private static final String OWNER_EMAIL = "owner.demo@mysuku.ma";
    private static final String CLIENT_EMAIL = "client.demo@mysuku.ma";
    private static final String LIVREUR_EMAIL = "livreur.demo@mysuku.ma";

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final CategoriesRestaurantRepository categoriesRestaurantRepository;
    private final ZoneDeploiementRepository zoneDeploiementRepository;
    private final PromotionRepository promotionRepository;
    private final RestaurantRepository restaurantRepository;
    private final PlatRepository platRepository;
    private final MenuRepository menuRepository;
    private final CommandeRepository commandeRepository;

    @Override
    @Transactional
    public void run(String... args) {
        final User admin = ensureUser(ADMIN_EMAIL, "Admin", "Demo", UserRole.ADMIN, false);
        final User owner = ensureUser(OWNER_EMAIL, "Restaurant", "Demo", UserRole.RESTAURANT_OWNER, true);
        final User client = ensureUser(CLIENT_EMAIL, "Client", "Demo", UserRole.CLIENT, false);
        final User livreur = ensureUser(LIVREUR_EMAIL, "Livreur", "Demo", UserRole.LIVREUR, true);

        final CategorieRestaurant categorie = categoriesRestaurantRepository.findByNom("Africain")
                .orElseGet(() -> {
                    CategorieRestaurant created = new CategorieRestaurant();
                    created.setNom("Africain");
                    created.setDescription("Restaurants africains");
                    created.setRestaurants(new ArrayList<>());
                    return categoriesRestaurantRepository.save(created);
                });

        final ZoneDeploiement zone = zoneDeploiementRepository.findAll().stream()
                .filter(z -> "Marrakech".equalsIgnoreCase(z.getNom()))
                .findFirst()
                .orElseGet(() -> zoneDeploiementRepository.save(
                        ZoneDeploiement.builder()
                                .nom("Marrakech")
                                .description("Zone de test Marrakech")
                                .centreLatitude(31.6295)
                                .centreLongitude(-7.9811)
                                .rayonKm(BigDecimal.valueOf(18))
                                .fraisLivraisonMin(BigDecimal.valueOf(15))
                                .distanceMinKm(BigDecimal.valueOf(3))
                                .prixExtraParKm(BigDecimal.valueOf(4))
                                .isActive(true)
                                .build()
                ));

        final Promotion promotion = promotionRepository.findAll().stream()
                .filter(p -> "BIENVENUE30".equalsIgnoreCase(p.getCode()))
                .findFirst()
                .orElseGet(() -> promotionRepository.save(
                        Promotion.builder()
                                .pourcentage(30)
                                .dateDebut(LocalDateTime.now().minusDays(1))
                                .dateFin(LocalDateTime.now().plusDays(30))
                                .description("Offre de test MySuku")
                                .isActive(true)
                                .code("BIENVENUE30")
                                .montantMinCommande(BigDecimal.valueOf(0))
                                .usageMax(500)
                                .usageCount(0)
                                .estFlash(false)
                                .build()
                ));

        final Restaurant restaurant = restaurantRepository.findAll().stream()
                .filter(r -> "Mama Afrika Demo".equalsIgnoreCase(r.getNom()))
                .findFirst()
                .orElseGet(() -> restaurantRepository.save(createRestaurant(categorie, owner, promotion, zone)));

        final Plat attieke = ensurePlat(
                "Attieke Poisson",
                "Attieke garni de poisson frit",
                BigDecimal.valueOf(55),
                restaurant,
                List.of("Attieke", "Poisson", "Oignon", "Piment"),
                CategoriePlat.PLAT_PRINCIPAL,
                18
        );

        final Plat pouletBraise = ensurePlat(
                "Poulet braise",
                "Poulet braise servi avec frites",
                BigDecimal.valueOf(75),
                restaurant,
                List.of("Poulet", "Frites", "Salade"),
                CategoriePlat.PLAT_PRINCIPAL,
                22
        );

        final Plat bissap = ensurePlat(
                "Bissap Maison",
                "Boisson hibiscus maison",
                BigDecimal.valueOf(20),
                restaurant,
                List.of("Hibiscus", "Menthe"),
                CategoriePlat.BOISSON,
                5
        );

        ensureMenu(restaurant, attieke, pouletBraise, bissap);
        ensureOrders(client, livreur, restaurant, attieke, pouletBraise, bissap);
    }

    private User ensureUser(String email, String nom, String prenom, UserRole role, boolean livreurDisponible) {
        return userRepository.findByEmail(email).orElseGet(() ->
                userRepository.save(createUser(email, nom, prenom, role, livreurDisponible))
        );
    }

    private User createUser(String email, String nom, String prenom, UserRole role, boolean livreurDisponible) {
        final User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("demo1234"));
        user.setNom(nom);
        user.setPrenom(prenom);
        user.setTelephone("+212600000000");
        user.setRole(role);
        user.setAvatar(null);
        user.setLocalisation(Localisation.builder()
                .latitude(31.6295)
                .longitude(-7.9811)
                .adresse("Marrakech")
                .ville("Marrakech")
                .codePostal("40000")
                .pays("Maroc")
                .build());
        user.setIsActive(true);
        user.setEmailVerified(true);
        user.setConsentRgpd(true);
        user.setIsDeleted(false);
        user.setDeletedAt(null);
        user.setLivreurDisponible(livreurDisponible);
        user.setAppleSub(null);
        return user;
    }

    private Restaurant createRestaurant(CategorieRestaurant categorie, User owner, Promotion promotion, ZoneDeploiement zone) {
        final Restaurant restaurant = new Restaurant();
        restaurant.setNom("Mama Afrika Demo");
        restaurant.setDescription("Restaurant de test pour valider les parcours MySuku");
        restaurant.setLogoUrl(null);
        restaurant.setAppreciation(4.7);
        restaurant.setNombreAvis(124);
        restaurant.setTempsLivraisonMoyen(35);
        restaurant.setLocalisation(Localisation.builder()
                .latitude(31.6295)
                .longitude(-7.9811)
                .adresse("Marrakech")
                .ville("Marrakech")
                .codePostal("40000")
                .pays("Maroc")
                .build());
        restaurant.setCategorie(categorie);
        restaurant.setOwner(owner);
        restaurant.setPromotion(promotion);
        restaurant.setZoneDeploiement(zone);
        restaurant.setPlats(new ArrayList<>());
        restaurant.setIsActive(true);
        restaurant.setAutoCloseEnabled(false);
        restaurant.setHeureOuverture(null);
        restaurant.setHeureFermeture(null);
        restaurant.setHorairesOuverture("Lun-Dim 10:00-23:00");
        restaurant.setCommissionPourcentage(BigDecimal.valueOf(12));
        return restaurant;
    }

    private Plat ensurePlat(
            String nom,
            String description,
            BigDecimal prix,
            Restaurant restaurant,
            List<String> ingredients,
            CategoriePlat categoriePlat,
            Integer tempsPreparation
    ) {
        return platRepository.findAll().stream()
                .filter(p -> nom.equalsIgnoreCase(p.getNom()) && p.getRestaurant() != null && p.getRestaurant().getId().equals(restaurant.getId()))
                .findFirst()
                .orElseGet(() -> platRepository.save(
                        new Plat(
                                null,
                                nom,
                                description,
                                prix,
                                null,
                                new ArrayList<>(ingredients),
                                restaurant,
                                true,
                                ModeDisponibilitePlat.DISPONIBLE,
                                null,
                                tempsPreparation,
                                categoriePlat
                        )
                ));
    }

    private void ensureMenu(Restaurant restaurant, Plat... plats) {
        final boolean exists = menuRepository.findAll().stream()
                .anyMatch(menu -> "Menu Demo".equalsIgnoreCase(menu.getNom())
                        && menu.getRestaurant() != null
                        && menu.getRestaurant().getId().equals(restaurant.getId()));
        if (exists) {
            return;
        }

        final Menu menu = Menu.builder()
                .restaurant(restaurant)
                .nom("Menu Demo")
                .description("Menu de test pour valider l'affichage des plats")
                .heureDebut(null)
                .heureFin(null)
                .joursSemaine("1,2,3,4,5,6,7")
                .isActive(true)
                .menuPlats(new ArrayList<>())
                .build();

        final List<MenuPlat> menuPlats = new ArrayList<>();
        for (int i = 0; i < plats.length; i++) {
            menuPlats.add(MenuPlat.builder()
                    .menu(menu)
                    .plat(plats[i])
                    .prixSpecial(i == 0 ? plats[i].getPrix().subtract(BigDecimal.valueOf(5)) : null)
                    .ordreAffichage(i + 1)
                    .build());
        }
        menu.setMenuPlats(menuPlats);
        menuRepository.save(menu);
    }

    private void ensureOrders(User client, User livreur, Restaurant restaurant, Plat... plats) {
        ensureOrder(
                "MSK-TEST-0001",
                client,
                restaurant,
                null,
                StatutCommande.EN_PREPARATION,
                plats[0],
                plats[1]
        );

        ensureOrder(
                "MSK-TEST-0002",
                client,
                restaurant,
                livreur,
                StatutCommande.LIVREE,
                plats[1],
                plats[2]
        );
    }

    private void ensureOrder(
            String numeroCommande,
            User client,
            Restaurant restaurant,
            User livreur,
            StatutCommande statut,
            Plat platA,
            Plat platB
    ) {
        if (commandeRepository.findByNumeroCommande(numeroCommande).isPresent()) {
            return;
        }

        final Commande commande = new Commande();
        commande.setNumeroCommande(numeroCommande);
        commande.setClient(client);
        commande.setRestaurant(restaurant);
        commande.setLivreur(livreur);
        commande.setStatut(statut);
        commande.setAdresseLivraison(Localisation.builder()
                .latitude(31.628)
                .longitude(-7.987)
                .adresse("Gueliz, Marrakech")
                .ville("Marrakech")
                .codePostal("40000")
                .pays("Maroc")
                .build());
        commande.setModeReception(ModeReceptionCommande.LIVRAISON);
        commande.setMontantTotal(BigDecimal.valueOf(130));
        commande.setMontantRemise(BigDecimal.ZERO);
        commande.setMontantFinal(BigDecimal.valueOf(130));
        commande.setFraisLivraison(BigDecimal.valueOf(15));
        commande.setTempsLivraisonEstime(35);
        commande.setCommentaire("Commande de test real");
        commande.setMethodePaiement(MethodePaiement.ESPECES);
        commande.setStatutPaiement(StatutPaiement.EN_ATTENTE);
        commande.setMontantCommissionTotal(BigDecimal.valueOf(13));
        commande.setIsReorder(false);
        commande.setTiktakOrderId(null);
        commande.setTiktakSyncStatus("SYNCED");

        final LigneCommande ligne1 = new LigneCommande();
        ligne1.setCommande(commande);
        ligne1.setPlat(platA);
        ligne1.setQuantite(1);
        ligne1.setPrixUnitaire(platA.getPrix());
        ligne1.setMontantTotal(platA.getPrix());
        ligne1.setCommissionPourcentage(BigDecimal.valueOf(10));
        ligne1.setMontantCommission(platA.getPrix().multiply(BigDecimal.valueOf(0.10)));

        final LigneCommande ligne2 = new LigneCommande();
        ligne2.setCommande(commande);
        ligne2.setPlat(platB);
        ligne2.setQuantite(1);
        ligne2.setPrixUnitaire(platB.getPrix());
        ligne2.setMontantTotal(platB.getPrix());
        ligne2.setCommissionPourcentage(BigDecimal.valueOf(10));
        ligne2.setMontantCommission(platB.getPrix().multiply(BigDecimal.valueOf(0.10)));

        commande.setLignesCommande(new ArrayList<>(List.of(ligne1, ligne2)));
        commandeRepository.save(commande);
    }
}
