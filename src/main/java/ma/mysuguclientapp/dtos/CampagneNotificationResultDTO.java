package ma.mysuguclientapp.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CampagneNotificationResultDTO {

    /** Nombre total de destinataires ciblés. */
    private int destinatairesCount;

    /** Nombre de notifications in-app sauvegardées en base. */
    private int notificationsCreees;

    /** Nombre de tokens FCM auxquels un push a été tenté. */
    private int pushEnvoyees;

    private LocalDateTime envoyeeAt;
}
