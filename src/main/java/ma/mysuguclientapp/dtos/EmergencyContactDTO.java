package ma.mysuguclientapp.dtos;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class EmergencyContactDTO {
    private String name;
    private String phone;
    private String whatsapp;
}
