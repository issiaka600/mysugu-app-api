package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class ServiceCategorieDTO {
    private Long id;
    private String nom;
    private String tag;
    private String description;
    private String imageTopUrl;
    private String imageBannerUrl;
    private String icon;
    private String type;
    private String vertical;
    private Integer ordre;
    private Boolean isActive;
}
