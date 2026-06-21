package ma.mysuguclientapp.dtos.integration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TikTakSharedIdsDTO {
    private List<TikTakIdMappingDTO> restaurantSellerMap;
    private List<TikTakIdMappingDTO> deliveryManMap;
    private boolean identityFallback;
}
