package ma.mysuguclientapp.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("conversation_id")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long conversationId;
    @JsonProperty("order_id")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long orderId;
    @JsonProperty("sender_id")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long senderId;
    @JsonProperty("sender_type")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String senderType;
    private LocalDateTime createdAt;
}
