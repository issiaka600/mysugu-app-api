package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class ContactUrgenceCreateDTO {
    /** null = contact global plateforme (visible pour tous les livreurs) */
    private Long restaurantId;
    private String nom;
    private String telephone;
}