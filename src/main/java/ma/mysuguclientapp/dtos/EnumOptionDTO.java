package ma.mysuguclientapp.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EnumOptionDTO {
    private String value;
    private String label;
}
