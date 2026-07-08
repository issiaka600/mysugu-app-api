package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageChatDTO {
    private Long id;
    private Long conversationId;
    private Long expediteurId;
    private String expediteurNom;
    private String expediteurPrenom;
    private String contenu;
    private String imageUrl;
    private Boolean lu;
    private Boolean envoyeParMoi;
    private LocalDateTime createdAt;
}