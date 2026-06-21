package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.integration.TikTakMessageNotificationDTO;
import ma.mysuguclientapp.dtos.integration.TikTakSharedIdsDTO;
import ma.mysuguclientapp.dtos.integration.TikTakOrderStatusSyncDTO;
import ma.mysuguclientapp.services.integrations.TikTakOrderIntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/integrations/tiktak")
@RequiredArgsConstructor
public class TikTakIntegrationController {
    private final TikTakOrderIntegrationService tikTakOrderIntegrationService;

    @PostMapping("/orders/status")
    public ResponseEntity<?> syncOrderStatus(
            @RequestHeader(value = "X-Integration-Token", required = false) String token,
            @RequestBody TikTakOrderStatusSyncDTO statusDTO) {
        if (!tikTakOrderIntegrationService.isValidToken(token)) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        tikTakOrderIntegrationService.applyStatusFromTikTak(statusDTO);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/shared-ids")
    public ResponseEntity<TikTakSharedIdsDTO> sharedIds() {
        return ResponseEntity.ok(tikTakOrderIntegrationService.getSharedIds());
    }

    @PostMapping("/messages/notify")
    public ResponseEntity<?> notifyMessage(
            @RequestHeader(value = "X-Integration-Token", required = false) String token,
            @RequestBody TikTakMessageNotificationDTO dto) {
        if (!tikTakOrderIntegrationService.isValidToken(token)) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        tikTakOrderIntegrationService.notifyMessageFromTikTak(dto);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
