package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationDTO {
    private Long id;

    private Long clientId;
    private String clientNom;
    private String clientPrenom;
    private String clientAvatar;

    private Long restaurantId;
    private String restaurantNom;
    private String restaurantLogo;

    private Long commandeId;

    private String dernierMessage;
    private LocalDateTime dernierMessageAt;
    private long nombreNonLus;

    private LocalDateTime createdAt;
}