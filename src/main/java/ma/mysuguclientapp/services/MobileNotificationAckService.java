package ma.mysuguclientapp.services;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.MobileNotificationAckDTO;
import ma.mysuguclientapp.entities.MobileNotificationAck;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.repositories.MobileNotificationAckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MobileNotificationAckService {
    private final MobileNotificationAckRepository repository;

    @Transactional
    public MobileNotificationAck record(MobileNotificationAckDTO dto, User user) {
        return repository.save(MobileNotificationAck.builder()
                .user(user)
                .event(dto.getEvent())
                .notificationId(dto.getNotificationId())
                .orderId(dto.getOrderId())
                .deliveryOfferId(dto.getDeliveryOfferId())
                .firebaseMessageId(dto.getFirebaseMessageId())
                .deviceToken(dto.getDeviceToken())
                .occurredAt(dto.getOccurredAt() != null ? dto.getOccurredAt() : LocalDateTime.now())
                .build());
    }
}
