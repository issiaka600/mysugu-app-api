package ma.mysuguclientapp.dtos.integration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TikTakIdMappingDTO {
    private Long sourceId;
    private Long targetId;
}
