package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class LigneCommandeCreateDTO {
    private Long platId;
    private Integer quantite;
    private String remarque;
    private java.util.List<Long> optionItemIds;
}