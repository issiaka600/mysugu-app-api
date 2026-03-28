package ma.mysuguclientapp.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CampagneNotificationRequestDTO {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 200, message = "Le titre ne peut pas dépasser 200 caractères")
    private String titre;

    @NotBlank(message = "Le message est obligatoire")
    @Size(max = 1000, message = "Le message ne peut pas dépasser 1000 caractères")
    private String message;

    /**
     * Type de notification : PROMOTION ou SYSTEME.
     * Défaut : PROMOTION.
     */
    private String type = "PROMOTION";

    /**
     * Rôle cible : CLIENT, LIVREUR, RESTAURANT_OWNER ou ALL (tous les utilisateurs actifs).
     * Défaut : CLIENT.
     */
    private String cibleRole = "CLIENT";

    /** Identifiant de l'entité liée (ex. id d'une promotion). Optionnel. */
    private Long entityId;

    /** Type de l'entité liée (ex. "PROMOTION"). Optionnel. */
    private String entityType;

    /**
     * ID d'un utilisateur spécifique à cibler.
     * Quand ce champ est renseigné, {@code cibleRole} est ignoré
     * et la notification est envoyée uniquement à cet utilisateur.
     */
    private Long destinataireUserId;
}
