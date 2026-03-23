package ma.mysuguclientapp.dtos.cart;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PanierDTO {
    private Long id;
    private Long restaurantId;
    private String restaurantNom;
    private List<PanierItemDTO> items;
    private BigDecimal montantTotal;
    private Integer nombreItems;
    private LocalDateTime updatedAt;
}
