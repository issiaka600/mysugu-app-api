package ma.mysuguclientapp.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Coordonnées minimales d'un participant à une commande.
 *
 * <p>{@code chat_user_id} est l'identifiant à transmettre aux API de messagerie. Pour un
 * restaurant, il correspond à son identifiant de restaurant (les conversations vendeur sont
 * rattachées au restaurant, pas au compte propriétaire).</p>
 */
@Data
public class CommandeContactDTO {
    private Long id;

    @JsonProperty("chat_user_id")
    private Long chatUserId;

    @JsonProperty("participant_type")
    private String participantType;

    private String name;
    private String phone;
    private String avatar;

    @JsonProperty("restaurant_id")
    private Long restaurantId;

    @JsonProperty("owner_user_id")
    private Long ownerUserId;
}
