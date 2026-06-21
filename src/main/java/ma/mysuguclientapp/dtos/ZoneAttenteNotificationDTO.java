package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ZoneAttenteNotificationDTO {
    private Long id;
    private String email;
    private String telephone;
    private Double latitude;
    private Double longitude;
    private String ville;
    private String pays;
    private Boolean notifie;
    private LocalDateTime createdAt;
}
