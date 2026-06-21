package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.util.List;

@Data
public class OptionGroupDTO {
    private Long id;
    private String nom;
    private String selectionMode;   // SINGLE | MULTIPLE
    private Boolean obligatoire;
    private Integer minSelections;
    private Integer maxSelections;
    private Integer ordre;
    private List<OptionItemDTO> items;
}
