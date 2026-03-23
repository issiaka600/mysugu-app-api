package ma.mysuguclientapp.dtos.restaurant;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RestaurantEmployeDTO {
    private Long id;
    private Long restaurantId;
    private String restaurantNom;
    private Long userId;
    private String userNom;
    private String userPrenom;
    private String userEmail;
    private String poste;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
