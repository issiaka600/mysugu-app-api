package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class MessageChatCreateDTO {
    /** Optionnel si conversationId fourni (répondre dans un fil existant) */
    private Long conversationId;

    /** Requis pour démarrer une nouvelle conversation (côté client uniquement) */
    private Long restaurantId;

    /** Optionnel : rattacher la conversation à une commande précise */
    private Long commandeId;

    private String contenu;
    private String imageUrl;
}