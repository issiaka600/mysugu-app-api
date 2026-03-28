package ma.mysuguclientapp.dtos;

import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationDTO {
    private Long id;
    private Long destinataireId;
    private String destinataireNom;
    private String destinatairePrenom;
    private String destinataireEmail;
    private String titre;
    private String message;
    private String type;
    private Boolean lue;
    private LocalDateTime lueAt;
    private Long entityId;
    private String entityType;
    private LocalDateTime createdAt;
}
