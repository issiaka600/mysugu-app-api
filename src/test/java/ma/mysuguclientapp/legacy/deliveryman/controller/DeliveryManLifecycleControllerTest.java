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
import ma.mysuguclientapp.services.implementations.DispatchLivraisonService;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeliveryManLifecycleControllerTest {

    private final CommandeRepository commandes = mock(CommandeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final FcmService fcm = mock(FcmService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final DispatchLivraisonService dispatch = mock(DispatchLivraisonService.class);
    private final DeliveryManLifecycleController controller = new DeliveryManLifecycleController(
            commandes,
            users,
            mock(GainsLivreurServiceImpl.class),
            mock(CaisseServiceImpl.class),
            notifications,
            mock(MinioService.class),
            mock(PreuveLivraisonRepository.class),
            fcm,
            mock(StockService.class),
            mock(CommandeStatusHistoryService.class),
            dispatch);

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
        when(commandes.findByIdForUpdate(51L)).thenReturn(Optional.of(commande));
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
        assertThat(commande.getDeliveryOtpNotificationSentAt()).isNotNull();

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
    void otpEstEnvoyeUneSeuleFoisAvecUneCleIdempotence() {
        controller.updateOrderStatus(livreur.getEmail(),
                Map.of("order_id", 51L, "status", "out_for_delivery"));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, String>> payload = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(fcm).sendToUser(any(), any(), any(), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("event", "delivery_otp")
                .containsEntry("type", "order_status")
                .containsEntry("order_id", "51")
                .containsEntry("order_number", "CMD-51")
                .containsEntry("status", "out_for_delivery")
                .containsEntry("idempotency_key", "delivery-otp:51")
                .containsKeys("verification_code", "title", "body")
                .containsEntry("sound", "default");

        controller.resendVerificationCode(livreur.getEmail(), Map.of("order_id", 51L));

        verify(fcm, times(1)).sendToUser(any(), any(), any(), any());
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

    @Test
    void desistementLivreurNeDevientPasUneAnnulationEtNenvoieAucunPushMetier() {
        commande.setStatut(StatutCommande.EN_COURS);
        when(dispatch.desisterCommande(51L, livreur)).thenReturn(456L);

        var response = controller.updateOrderStatus(livreur.getEmail(),
                Map.of("order_id", 51L, "status", "canceled", "cause", "Indisponible"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
                .containsEntry("event", "delivery_offer_declined")
                .containsEntry("type", "delivery_offer")
                .containsEntry("order_id", 51L)
                .containsEntry("delivery_offer_id", 456L)
                .containsEntry("status", "declined")
                .containsEntry("declined_by", "courier");
        verify(dispatch).desisterCommande(51L, livreur);
        verifyNoInteractions(notifications);
        verifyNoInteractions(fcm);
    }
}
