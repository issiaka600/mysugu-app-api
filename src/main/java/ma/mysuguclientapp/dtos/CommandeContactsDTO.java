package ma.mysuguclientapp.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Contacts qu'un participant authentifié est autorisé à consulter pour une commande. */
@Data
public class CommandeContactsDTO {
    @JsonProperty("order_id")
    private Long orderId;

    private CommandeContactDTO customer;
    private CommandeContactDTO seller;

    @JsonProperty("delivery_man")
    private CommandeContactDTO deliveryMan;
}
