package ma.mysuguclientapp.services.implementations;

import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.dtos.NotificationDTO;
import ma.mysuguclientapp.entities.Notification;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.TypeNotification;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.NotificationRepository;
import ma.mysuguclientapp.repositories.TentativeNotificationFcmRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.FcmDeliveryResult;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final JwtTokenProvider jwt = mock(JwtTokenProvider.class);
    private final FcmService fcm = mock(FcmService.class);
    private final TentativeNotificationFcmRepository attempts = mock(TentativeNotificationFcmRepository.class);
    private final NotificationServiceImpl service = new NotificationServiceImpl(
            notifications, users, jwt, fcm, attempts);

    @Test
    void messageNotificationExposeLesDonneesDeRedirection() {
        User customer = new User();
        customer.setId(5L);
        customer.setEmail("customer@test.local");
        customer.setRole(UserRole.CLIENT);
        when(users.findById(5L)).thenReturn(Optional.of(customer));
        when(fcm.sendToUserWithResult(any(), any(), any(), any()))
                .thenReturn(new FcmDeliveryResult(1, 1, List.of("fcm-id"), List.of()));
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.envoyerNotificationMessage(5L, "12", "delivery_man", "Livreur",
                456L, 789L, 1L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        org.mockito.Mockito.verify(notifications).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getConversationId()).isEqualTo(456L);
        assertThat(saved.getOrderId()).isEqualTo(789L);
        assertThat(saved.getSenderId()).isEqualTo(12L);
        assertThat(saved.getSenderType()).isEqualTo("delivery_man");

        when(jwt.getEmailFromToken("token")).thenReturn(customer.getEmail());
        when(users.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(notifications.findByDestinataireIdOrderByCreatedAtDesc(5L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(saved)));

        NotificationDTO dto = service.getMesNotifications("Bearer token", PageRequest.of(0, 10))
                .getContent().getFirst();
        assertThat(dto.getType()).isEqualTo("message");
        assertThat(dto.getConversationId()).isEqualTo(456L);
        assertThat(dto.getOrderId()).isEqualTo(789L);
        assertThat(dto.getSenderId()).isEqualTo(12L);
        assertThat(dto.getSenderType()).isEqualTo("delivery_man");
    }

    @Test
    void orderNotificationExposeOrderStatusEtOrderId() {
        User customer = new User();
        customer.setId(5L);
        Notification notification = Notification.builder()
                .destinataire(customer)
                .type(TypeNotification.COMMANDE_PRETE)
                .titre("Commande prête")
                .message("Prête")
                .entityId(789L)
                .entityType("COMMANDE")
                .lue(false)
                .build();
        when(jwt.getEmailFromToken("token")).thenReturn("customer@test.local");
        when(users.findByEmail("customer@test.local")).thenReturn(Optional.of(customer));
        when(notifications.findByDestinataireIdOrderByCreatedAtDesc(5L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(notification)));

        NotificationDTO dto = service.getMesNotifications("Bearer token", PageRequest.of(0, 10))
                .getContent().getFirst();
        assertThat(dto.getType()).isEqualTo("order_status");
        assertThat(dto.getOrderId()).isEqualTo(789L);
    }

    @Test
    void annulationClientEnvoieLeContratAttendu() {
        User vendor = new User();
        vendor.setId(7L);
        when(users.findById(7L)).thenReturn(Optional.of(vendor));
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fcm.sendToUserWithResult(any(), any(), any(), any()))
                .thenReturn(new FcmDeliveryResult(1, 1, List.of("fcm-id"), List.of()));

        service.envoyerNotificationAnnulationCommande(
                7L, "CMD-2030", 2030L, "customer", "Le délai est trop long");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(fcm).sendToUserWithResult(
                org.mockito.ArgumentMatchers.eq(7L), any(), message.capture(), data.capture());

        assertThat(message.getValue()).isEqualTo("La commande CMD-2030 a été annulée par le client.");
        assertThat(data.getValue()).containsEntry("type", "order_status")
                .containsEntry("event", "order_canceled")
                .containsEntry("order_id", "2030")
                .containsEntry("status", "canceled")
                .containsEntry("canceled_by", "customer")
                .containsEntry("cancellation_reason", "Le délai est trop long");
    }
}
