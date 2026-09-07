package ma.mysuguclientapp.legacy.deliveryman.controller;

import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.MethodePaiement;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.PreuveLivraisonRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.CaisseServiceImpl;
import ma.mysuguclientapp.services.implementations.CommandeStatusHistoryService;
import ma.mysuguclientapp.services.implementations.GainsLivreurServiceImpl;
import ma.mysuguclientapp.services.implementations.MinioService;
import ma.mysuguclientapp.services.implementations.StockService;
import ma.mysuguclientapp.services.interfaces.FcmService;
import ma.mysuguclientapp.services.interfaces.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryManLifecycleControllerTest {

    private final CommandeRepository commandes = mock(CommandeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final FcmService fcm = mock(FcmService.class);
    private final DeliveryManLifecycleController controller = new DeliveryManLifecycleController(
            commandes,
            users,
            mock(GainsLivreurServiceImpl.class),
            mock(CaisseServiceImpl.class),
            mock(NotificationService.class),
            mock(MinioService.class),
            mock(PreuveLivraisonRepository.class),
            fcm,
            mock(StockService.class),
            mock(CommandeStatusHistoryService.class));

    private User livreur;
    private Commande commande;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "deliveryOtpDurationSeconds", 600L);
        livreur = new User();
        livreur.setId(18L);
        livreur.setEmail("livreur@test.local");
        livreur.setRole(UserRole.LIVREUR);
        commande = new Commande();
        commande.setId(51L);
        commande.setNumeroCommande("CMD-51");
        commande.setLivreur(livreur);
        User client = new User();
        client.setId(25L);
        client.setRole(UserRole.CLIENT);
        commande.setClient(client);
        commande.setStatut(StatutCommande.PRETE);
        commande.setMethodePaiement(MethodePaiement.CARTE_BANCAIRE);
        when(users.findByEmail(livreur.getEmail())).thenReturn(Optional.of(livreur));
        when(commandes.findById(51L)).thenReturn(Optional.of(commande));
        when(commandes.save(any(Commande.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void parcoursLivraisonGenerePuisConsommeOtpAvantDelivered() {
        var started = controller.updateOrderStatus(livreur.getEmail(),
                Map.of("order_id", 51L, "status", "out_for_delivery"));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_COURS);
        assertThat(commande.getCodeVerificationLivraison()).matches("\\d{6}");
        assertThat(commande.getDeliveryOtpExpiresAt()).isNotNull();
        verify(fcm).sendToUser(any(), any(), any(), any());

        String otp = commande.getCodeVerificationLivraison();
        var verified = controller.verifyDeliveryOtp(livreur.getEmail(),
                Map.of("order_id", 51L, "verification_code", otp));
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(commande.getLivraisonVerifiee()).isTrue();
        assertThat(commande.getDeliveryOtpVerifiedBy()).isSameAs(livreur);
        assertThat(commande.getDeliveryOtpVerifiedAt()).isNotNull();
        assertThat(commande.getCodeVerificationLivraison()).isNull();

        var delivered = controller.updateOrderStatus(livreur.getEmail(),
                Map.of("order_id", 51L, "status", "delivered"));
        assertThat(delivered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.LIVREE);
    }

    @Test
    void refuseDeliveredSansOtpValide() {
        commande.setStatut(StatutCommande.EN_COURS);

        var response = controller.updateOrderStatus(livreur.getEmail(),
                Map.of("order_id", 51L, "status", "delivered"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_COURS);
    }

    @Test
    void refuseOutForDeliveryAvantReady() {
        commande.setStatut(StatutCommande.ASSIGNEE_LIVREUR);

        var response = controller.updateOrderStatus(livreur.getEmail(),
                Map.of("order_id", 51L, "status", "out_for_delivery"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.ASSIGNEE_LIVREUR);
    }
}
