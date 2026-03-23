package ma.mysuguclientapp.dtos;

import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FavoriDTO {
    private Long id;
    private Long userId;
    private Long restaurantId;
    private String restaurantNom;
    private String restaurantLogoUrl;
    private Double restaurantAppreciation;
    private Boolean restaurantIsActive;
    private LocalDateTime createdAt;
}
