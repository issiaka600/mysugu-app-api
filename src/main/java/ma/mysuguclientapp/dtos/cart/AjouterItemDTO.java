package ma.mysuguclientapp.dtos.cart;

import lombok.Data;

@Data
public class AjouterItemDTO {
    private Long platId;
    private Integer quantite;
    private String remarque;
}
