package ma.mysuguclientapp.dtos;

import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AppSupportDTO {
    private String app;
    private String phone;
    private String whatsapp;
    private String email;
    private String address;
    private String workingHours;
    private String website;
    @Builder.Default
    private List<EmergencyContactDTO> emergencyContacts = List.of();
}
