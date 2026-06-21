package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class ZoneAttenteNotificationCreateDTO {
    private String email;
    private String telephone;
    private Double latitude;
    private Double longitude;
    private String ville;
    private String pays;
}
