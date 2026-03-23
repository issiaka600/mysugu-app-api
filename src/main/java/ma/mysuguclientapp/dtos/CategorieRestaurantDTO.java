package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class CategorieRestaurantDTO {
    private Long id;
    private String nom;
    private String description;
    private String imageUrl;
    private Integer nombreRestaurants;
}