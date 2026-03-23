package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromotionDTO {
    private Long id;
    private Integer pourcentage;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String description;
    private Boolean isActive;
}