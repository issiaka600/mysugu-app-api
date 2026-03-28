package ma.mysuguclientapp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CampagneNotificationRequestDTO;
import ma.mysuguclientapp.dtos.CampagneNotificationResultDTO;
import ma.mysuguclientapp.services.interfaces.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * API réservée à l'admin pour envoyer des campagnes de notifications
 * (promotions, messages système) aux utilisateurs de la plateforme.
 *
 * Toutes les notifications sont envoyées en in-app (base de données) ET en push FCM.
 */
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CampagneNotificationController {

    private final NotificationService notificationService;

    /**
     * Envoie une campagne de notification à un segment d'utilisateurs.
     *
     * <p>Corps de la requête :
     * <ul>
     *   <li>{@code titre} — titre affiché dans la notification (obligatoire, max 200 car.)</li>
     *   <li>{@code message} — corps du message (obligatoire, max 1000 car.)</li>
     *   <li>{@code type} — {@code PROMOTION} (défaut) ou {@code SYSTEME}</li>
     *   <li>{@code cibleRole} — {@code CLIENT} (défaut), {@code LIVREUR},
     *       {@code RESTAURANT_OWNER}, {@code ADMIN} ou {@code ALL}</li>
     *   <li>{@code entityId} — id d'une entité liée, ex. id d'une promotion (optionnel)</li>
     *   <li>{@code entityType} — ex. {@code "PROMOTION"} (optionnel)</li>
     * </ul>
     *
     * <p>Réponse :
     * <ul>
     *   <li>{@code destinatairesCount} — nombre d'utilisateurs ciblés</li>
     *   <li>{@code notificationsCreees} — notifications sauvegardées en base</li>
     *   <li>{@code pushEnvoyees} — nombre de tokens FCM auxquels un push a été tenté</li>
     *   <li>{@code envoyeeAt} — horodatage de l'envoi</li>
     * </ul>
     */
    @PostMapping("/campagne")
    public ResponseEntity<CampagneNotificationResultDTO> envoyerCampagne(
            @Valid @RequestBody CampagneNotificationRequestDTO request) {
        CampagneNotificationResultDTO result = notificationService.envoyerCampagne(request);
        return ResponseEntity.ok(result);
    }
}
