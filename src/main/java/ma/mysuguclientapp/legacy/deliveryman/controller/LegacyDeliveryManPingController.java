package ma.mysuguclientapp.legacy.deliveryman.controller;

import ma.mysuguclientapp.legacy.deliveryman.dto.MessageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur de fumée de la couche legacy livreur. Prouve que le namespace
 * /api/v2/delivery-man/* est routé et sécurisé (LIVREUR) sans dépendre encore
 * d'aucune logique métier. Sera retiré une fois les endpoints réels en place.
 */
@RestController
@RequestMapping("/api/v2/delivery-man")
public class LegacyDeliveryManPingController {

    @GetMapping("/_ping")
    @PreAuthorize("hasRole('LIVREUR')")
    public MessageResponse ping() {
        return new MessageResponse("delivery-man shim up");
    }
}
