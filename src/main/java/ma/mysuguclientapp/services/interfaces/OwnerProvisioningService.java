package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.RestaurantCreateDTO;
import ma.mysuguclientapp.entities.User;

public interface OwnerProvisioningService {
    /**
     * Résout le restaurateur d'un restaurant à créer :
     * - ownerId fourni → charge l'existant (valide le rôle).
     * - sinon ownerEmail fourni → réutilise si déjà RESTAURANT_OWNER, sinon crée le compte
     *   (invitation tokenisée si ownerSendInvite, mot de passe direct sinon).
     * - aucun → BadRequestException.
     */
    User resolveOrCreateOwner(RestaurantCreateDTO dto);
}
