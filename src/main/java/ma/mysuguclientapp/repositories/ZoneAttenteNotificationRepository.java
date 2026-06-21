package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.ZoneAttenteNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneAttenteNotificationRepository extends JpaRepository<ZoneAttenteNotification, Long> {
    List<ZoneAttenteNotification> findAllByOrderByCreatedAtDesc();
}
