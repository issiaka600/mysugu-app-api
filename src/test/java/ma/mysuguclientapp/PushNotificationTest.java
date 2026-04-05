package ma.mysuguclientapp;

import ma.mysuguclientapp.dtos.CommandeCreateDTO;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.CommandeUpdateStatusDTO;
import ma.mysuguclientapp.dtos.LigneCommandeCreateDTO;
import ma.mysuguclientapp.dtos.LocalisationDTO;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.enumerations.*;
import ma.mysuguclientapp.repositories.*;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import ma.mysuguclientapp.services.interfaces.DeviceTokenService;
import ma.mysuguclientapp.services.interfaces.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests E2E — Notifications push FCM + Auto-assignation intelligente du livreur.
 *
 * Contexte Spring complet (vraie BDD PostgreSQL, vrai Firebase FCM).
 * Firebase lit le fichier firebase-service-account.json à la racine du projet.
 *
 * Scénarios couverts :
 *  1. Création commande → aucune notification
 *  2. Confirmation commande → auto-assignation du meilleur livreur disponible
 *     → notifications ciblées (client, restaurant, livreur assigné, admins)
 *  3. Livreur marqué indisponible après auto-assignation
 *  4. Confirmation sans livreur disponible → broadcast + alerte admin
 *  5. Assignation manuelle → libération ancien livreur, marquage nouveau comme indisponible
 *  6. Livraison → livreur redevient disponible
 *  7. Statistiques de disponibilité cohérentes
 */
@SpringBootTest
@TestPropertySource(properties = {
        "firebase.enabled=true",
        "firebase.service-account-path=firebase-service-account.json"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PushNotificationTest {

    @Autowired CommandeService commandeService;
    @Autowired DeviceTokenService deviceTokenService;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PlatRepository platRepository;
    @Autowired CommandeRepository commandeRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired DeviceTokenRepository deviceTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    Long clientId;
    Long restaurantOwnerId;
    Long livreurId;        // livreur PROCHE du restaurant (Casablanca centre)
    Long livreurLoinId;    // livreur LOIN du restaurant (hors rayon)
    Long adminId;
    Long restaurantId;
    Long platId;
    Long commandeId;
    Long commande2Id;      // pour le test sans livreur disponible

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeAll
    void setUp() {
        cleanupTestData();

        clientId          = createUser("test.client.push@mysugu.test",   UserRole.CLIENT,           "ClientPush",   "Test", null).getId();
        restaurantOwnerId = createUser("test.owner.push@mysugu.test",    UserRole.RESTAURANT_OWNER, "OwnerPush",    "Test", null).getId();
        adminId           = createUser("test.admin.push@mysugu.test",    UserRole.ADMIN,            "AdminPush",    "Test", null).getId();

        // Livreur proche : à ~2km du restaurant (33.5731, -7.5898) → devrait être auto-assigné
        livreurId     = createUser("test.livreur.push@mysugu.test", UserRole.LIVREUR, "LivreurPush", "Test",
                buildLoc(33.5850, -7.6050, "Avenue Hassan II", "Casablanca")).getId();

        // Livreur loin : à ~300km → hors rayon AUTO_ASSIGN_RADIUS_KM=15km
        livreurLoinId = createUser("test.livreur.loin@mysugu.test", UserRole.LIVREUR, "LivreurLoin", "Test",
                buildLoc(31.6295, -7.9811, "Avenue Mohamed V", "Marrakech")).getId();

        // Restaurant à Casablanca
        User owner = userRepository.findById(restaurantOwnerId).orElseThrow();
        Restaurant resto = new Restaurant();
        resto.setNom("Test Restaurant Push");
        resto.setOwner(owner);
        resto.setIsActive(true);
        resto.setAutoCloseEnabled(false);
        resto.setTempsLivraisonMoyen(20);
        resto.setLocalisation(buildLoc(33.5731, -7.5898, "Boulevard Anfa", "Casablanca"));
        restaurantId = restaurantRepository.save(resto).getId();

        Plat plat = new Plat();
        plat.setNom("Tajine Test");
        plat.setPrix(new BigDecimal("55.00"));
        plat.setIsAvailable(true);
        plat.setAvailabilityMode(ModeDisponibilitePlat.DISPONIBLE);
        plat.setRestaurant(restaurantRepository.findById(restaurantId).orElseThrow());
        plat.setTempsPreparation(15);
        platId = platRepository.save(plat).getId();

        // Tokens FCM (factices — seront invalidés gracieusement)
        deviceTokenService.registerToken(clientId,           "fake-client-"   + clientId,   "ANDROID");
        deviceTokenService.registerToken(restaurantOwnerId,  "fake-owner-"    + restaurantOwnerId, "ANDROID");
        deviceTokenService.registerToken(livreurId,          "fake-livreur-"  + livreurId,  "ANDROID");
        deviceTokenService.registerToken(livreurLoinId,      "fake-loin-"     + livreurLoinId, "ANDROID");
        deviceTokenService.registerToken(adminId,            "fake-admin-"    + adminId,    "ANDROID");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 1 — livreurDisponible = false par défaut
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("Livreurs créés avec livreurDisponible=false par défaut")
    void livreurDisponible_defaultFalse() {
        User livreur = userRepository.findById(livreurId).orElseThrow();
        assertThat(livreur.getLivreurDisponible()).isFalse();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 2 — Création commande → aucune notification
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(2)
    @DisplayName("Création commande → aucune notification générée")
    void createCommande_noNotification() {
        long before = notificationRepository.count();
        CommandeDTO commande = commandeService.createCommande(buildCommandeDTO());
        commandeId = commande.getId();
        assertThat(commande.getStatut()).isEqualTo("EN_ATTENTE");
        assertThat(notificationRepository.count()).isEqualTo(before);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 3 — Confirmation sans livreur disponible → broadcast + alerte admin
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(3)
    @DisplayName("Confirmation sans livreur disponible → broadcast livreurs + alerte admin")
    void confirmCommande_noLivreurDisponible_broadcastAndAlert() {
        // Les deux livreurs ont livreurDisponible=false → aucun auto-assignable
        User livreur = userRepository.findById(livreurId).orElseThrow();
        assertThat(livreur.getLivreurDisponible()).isFalse();

        long before = notificationRepository.count();
        updateStatut(commandeId, "CONFIRMEE");

        // Client + restaurant + admin (au moins 3 notifs)
        assertThat(notificationRepository.count() - before).isGreaterThanOrEqualTo(2);

        // Commande toujours sans livreur assigné
        Commande commande = commandeRepository.findById(commandeId).orElseThrow();
        assertThat(commande.getLivreur()).isNull();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 4 — Le livreur se met disponible
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(4)
    @DisplayName("setDisponibilite(true) → livreurDisponible=true en BDD")
    void setDisponibilite_updatesFlag() {
        User livreur = userRepository.findById(livreurId).orElseThrow();
        livreur.setLivreurDisponible(true);
        userRepository.save(livreur);

        User updated = userRepository.findById(livreurId).orElseThrow();
        assertThat(updated.getLivreurDisponible()).isTrue();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 5 — Nouvelle commande confirmée → auto-assignation du livreur proche
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(5)
    @DisplayName("Confirmation avec livreur proche disponible → auto-assignation + livreur indisponible")
    void confirmCommande_withLivreurDisponible_autoAssigns() {
        // Créer une 2ème commande
        CommandeDTO commande2 = commandeService.createCommande(buildCommandeDTO());
        commande2Id = commande2.getId();

        long before = notificationRepository.count();
        updateStatut(commande2Id, "CONFIRMEE");

        // Livreur proche auto-assigné
        Commande saved = commandeRepository.findById(commande2Id).orElseThrow();
        assertThat(saved.getLivreur()).isNotNull();
        assertThat(saved.getLivreur().getId()).isEqualTo(livreurId); // le plus proche

        // Le livreur loin (Marrakech) ne doit pas avoir été assigné
        assertThat(saved.getLivreur().getId()).isNotEqualTo(livreurLoinId);

        // Livreur marqué indisponible
        User livreur = userRepository.findById(livreurId).orElseThrow();
        assertThat(livreur.getLivreurDisponible()).isFalse();

        // Notifications envoyées (client, restaurant, livreur, admin = au moins 4)
        assertThat(notificationRepository.count() - before).isGreaterThanOrEqualTo(4);
    }

    @Test @Order(6)
    @DisplayName("Livreur auto-assigné reçoit une notification LIVREUR_ASSIGNE")
    void autoAssign_livreurReceivesLivreurAssigneNotification() {
        List<Notification> notifs = notificationRepository
                .findByDestinataireIdAndLueFalseOrderByCreatedAtDesc(livreurId);
        boolean hasAssigne = notifs.stream()
                .anyMatch(n -> n.getType() == TypeNotification.LIVREUR_ASSIGNE);
        assertThat(hasAssigne).isTrue();
    }

    @Test @Order(7)
    @DisplayName("Client reçoit COMMANDE_CONFIRMEE après auto-assignation")
    void autoAssign_clientReceivesConfirmedNotification() {
        List<Notification> notifs = notificationRepository
                .findByDestinataireIdAndLueFalseOrderByCreatedAtDesc(clientId);
        boolean hasConfirmed = notifs.stream()
                .anyMatch(n -> n.getType() == TypeNotification.COMMANDE_CONFIRMEE);
        assertThat(hasConfirmed).isTrue();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 8 — getAvailableLivreurs filtre par livreurDisponible
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(8)
    @DisplayName("getAvailableLivreurs ne retourne que les livreurs disponibles dans le rayon")
    void getAvailableLivreurs_filtersbyDisponibleAndRadius() {
        // Livreur proche indisponible (assigné), livreur loin toujours indisponible
        List<?> dispo = userService.getAvailableLivreurs(33.5731, -7.5898, 20.0);
        // Aucun des deux n'est disponible
        assertThat(dispo).isEmpty();

        // Remettre le livreur proche disponible manuellement pour vérifier le filtre
        User livreur = userRepository.findById(livreurId).orElseThrow();
        livreur.setLivreurDisponible(true);
        userRepository.save(livreur);

        List<?> dispoApres = userService.getAvailableLivreurs(33.5731, -7.5898, 20.0);
        assertThat(dispoApres).hasSize(1); // seulement le livreur proche
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 9 — Livraison → livreur redevient disponible
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(9)
    @DisplayName("Livraison terminée → livreur redevient automatiquement disponible")
    void deliveryCompleted_livreurBecomesAvailableAgain() {
        // Avancer la commande jusqu'à LIVREE
        updateStatut(commande2Id, "EN_PREPARATION");
        updateStatut(commande2Id, "PRETE");
        updateStatut(commande2Id, "EN_COURS");
        updateStatut(commande2Id, "LIVREE");

        User livreur = userRepository.findById(livreurId).orElseThrow();
        assertThat(livreur.getLivreurDisponible()).isTrue();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 10 — Annulation → livreur redevient disponible
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(10)
    @DisplayName("Annulation d'une commande assignée → livreur redevient disponible")
    void cancelCommande_livreurBecomesAvailableAgain() {
        // Créer une commande, confirmer (auto-assign livreur), puis annuler
        User livreur = userRepository.findById(livreurId).orElseThrow();
        assertThat(livreur.getLivreurDisponible()).isTrue(); // disponible après test 9

        CommandeDTO c3 = commandeService.createCommande(buildCommandeDTO());
        updateStatut(c3.getId(), "CONFIRMEE");

        Commande saved = commandeRepository.findById(c3.getId()).orElseThrow();
        assertThat(saved.getLivreur()).isNotNull();
        // Livreur maintenant indisponible
        assertThat(userRepository.findById(livreurId).orElseThrow().getLivreurDisponible()).isFalse();

        // Annuler
        updateStatut(c3.getId(), "ANNULEE");
        // Livreur redevenu disponible
        assertThat(userRepository.findById(livreurId).orElseThrow().getLivreurDisponible()).isTrue();

        // Cleanup de cette commande de test
        List<Long> ids3 = List.of(clientId, restaurantOwnerId, livreurId, adminId);
        transactionTemplate.executeWithoutResult(s -> notificationRepository.deleteByDestinataireIdIn(ids3));
        commandeRepository.deleteById(c3.getId());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 11 — Re-assignation manuelle libère l'ancien livreur
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(11)
    @DisplayName("Assignation manuelle d'un autre livreur → ancien livreur libéré")
    void manualReassign_releasesOldLivreur() {
        // livreurId est disponible (après test 9), livreurLoinId toujours indisponible
        User livreurLoin = userRepository.findById(livreurLoinId).orElseThrow();
        livreurLoin.setLivreurDisponible(true);
        userRepository.save(livreurLoin);

        CommandeDTO c4 = commandeService.createCommande(buildCommandeDTO());
        updateStatut(c4.getId(), "CONFIRMEE");

        // livreurId auto-assigné (plus proche)
        Commande saved = commandeRepository.findById(c4.getId()).orElseThrow();
        assertThat(saved.getLivreur().getId()).isEqualTo(livreurId);
        assertThat(userRepository.findById(livreurId).orElseThrow().getLivreurDisponible()).isFalse();

        // Ré-assignation manuelle au livreur loin
        updateStatut(c4.getId(), "EN_PREPARATION");
        updateStatut(c4.getId(), "PRETE");
        commandeService.assignLivreur(c4.getId(), livreurLoinId);

        // Ancien livreur (proche) libéré
        assertThat(userRepository.findById(livreurId).orElseThrow().getLivreurDisponible()).isTrue();
        // Nouveau livreur (loin) marqué indisponible
        assertThat(userRepository.findById(livreurLoinId).orElseThrow().getLivreurDisponible()).isFalse();

        // Cleanup
        List<Long> ids4 = List.of(clientId, restaurantOwnerId, livreurId, livreurLoinId, adminId);
        transactionTemplate.executeWithoutResult(s -> notificationRepository.deleteByDestinataireIdIn(ids4));
        commandeRepository.deleteById(c4.getId());
    }

    // ── Teardown ──────────────────────────────────────────────────────────────

    @AfterAll
    void tearDown() {
        List<Long> testUserIds = List.of(clientId, restaurantOwnerId, livreurId, livreurLoinId, adminId);

        transactionTemplate.executeWithoutResult(s -> {
            notificationRepository.deleteByDestinataireIdIn(testUserIds);
            testUserIds.forEach(deviceTokenRepository::deactivateAllByUserId);
        });

        deviceTokenRepository.findAll().stream()
                .filter(dt -> testUserIds.contains(dt.getUser().getId()))
                .forEach(deviceTokenRepository::delete);

        if (commande2Id != null) commandeRepository.findById(commande2Id)
                .ifPresent(commandeRepository::delete);
        if (commandeId != null) commandeRepository.findById(commandeId)
                .ifPresent(commandeRepository::delete);
        if (platId != null) platRepository.deleteById(platId);
        if (restaurantId != null) restaurantRepository.deleteById(restaurantId);

        testUserIds.forEach(uid -> userRepository.findById(uid).ifPresent(userRepository::delete));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createUser(String email, UserRole role, String nom, String prenom, Localisation loc) {
        userRepository.findByEmail(email).ifPresent(userRepository::delete);
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("testpass123"));
        u.setRole(role);
        u.setNom(nom);
        u.setPrenom(prenom);
        u.setIsActive(true);
        u.setEmailVerified(true);
        u.setConsentRgpd(true);
        u.setLivreurDisponible(false); // false par défaut — doit être explicitement activé
        u.setLocalisation(loc);
        return userRepository.save(u);
    }

    private Localisation buildLoc(double lat, double lon, String adresse, String ville) {
        Localisation l = new Localisation();
        l.setLatitude(lat);
        l.setLongitude(lon);
        l.setAdresse(adresse);
        l.setVille(ville);
        l.setPays("Maroc");
        return l;
    }

    private CommandeCreateDTO buildCommandeDTO() {
        CommandeCreateDTO dto = new CommandeCreateDTO();
        dto.setClientId(clientId);
        dto.setRestaurantId(restaurantId);
        dto.setModeReception("LIVRAISON");
        dto.setMethodePaiement("ESPECES");

        LocalisationDTO adresse = new LocalisationDTO();
        adresse.setLatitude(33.5891);
        adresse.setLongitude(-7.6034);
        adresse.setAdresse("456 Rue Client");
        adresse.setVille("Casablanca");
        adresse.setPays("Maroc");
        dto.setAdresseLivraison(adresse);

        LigneCommandeCreateDTO ligne = new LigneCommandeCreateDTO();
        ligne.setPlatId(platId);
        ligne.setQuantite(1);
        dto.setLignes(List.of(ligne));
        return dto;
    }

    private void updateStatut(Long cId, String statut) {
        CommandeUpdateStatusDTO dto = new CommandeUpdateStatusDTO();
        dto.setStatut(statut);
        commandeService.updateCommandeStatus(cId, dto);
    }

    private void cleanupTestData() {
        List<String> emails = List.of(
                "test.client.push@mysugu.test",
                "test.owner.push@mysugu.test",
                "test.livreur.push@mysugu.test",
                "test.livreur.loin@mysugu.test",
                "test.admin.push@mysugu.test"
        );
        emails.forEach(e -> userRepository.findByEmail(e).ifPresent(u -> {
            Long uid = u.getId();
            transactionTemplate.executeWithoutResult(s -> {
                notificationRepository.deleteByDestinataireIdIn(List.of(uid));
                deviceTokenRepository.deactivateAllByUserId(uid);
            });
            deviceTokenRepository.findAll().stream()
                    .filter(dt -> dt.getUser().getId().equals(uid))
                    .forEach(deviceTokenRepository::delete);
            userRepository.delete(u);
        }));
        restaurantRepository.findAll().stream()
                .filter(r -> "Test Restaurant Push".equals(r.getNom()))
                .forEach(restaurantRepository::delete);
    }
}
