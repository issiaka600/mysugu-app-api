package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.OptionGroupDTO;
import ma.mysuguclientapp.services.interfaces.PlatOptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plats/{platId}/options")
@RequiredArgsConstructor
public class PlatOptionController {

    private final PlatOptionService platOptionService;

    /** Lecture publique des sections d'options d'un plat. */
    @GetMapping
    public ResponseEntity<List<OptionGroupDTO>> getOptions(@PathVariable Long platId) {
        return ResponseEntity.ok(platOptionService.getOptions(platId));
    }

    /** Remplace toute la structure d'options (ADMIN ou restaurateur propriétaire). */
    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public ResponseEntity<List<OptionGroupDTO>> replaceOptions(
            @PathVariable Long platId,
            @RequestBody List<OptionGroupDTO> groups,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(platOptionService.replaceOptions(platId, groups, email));
    }
}
