package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.ZoneAttenteNotificationDTO;
import ma.mysuguclientapp.services.interfaces.ZoneAttenteNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/zones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminZonesController {

    private final ZoneAttenteNotificationService zoneAttenteNotificationService;

    @GetMapping("/demandes-hors-zone")
    public ResponseEntity<List<ZoneAttenteNotificationDTO>> getDemandesHorsZone() {
        return ResponseEntity.ok(zoneAttenteNotificationService.listerDemandes());
    }
}
