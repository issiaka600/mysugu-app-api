package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContactUrgenceDTO {
    private Long id;
    private Long restaurantId;
    private String restaurantNom;
    private String nom;
    private String telephone;
    private Boolean actif;
    private LocalDateTime createdAt;
}