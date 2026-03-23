package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByDestinataireIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Notification> findByDestinataireIdAndLueFalseOrderByCreatedAtDesc(Long userId);

    long countByDestinataireIdAndLueFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.lue = true, n.lueAt = :now WHERE n.destinataire.id = :userId AND n.lue = false")
    void marquerToutesCommeVues(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
