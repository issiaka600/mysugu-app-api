package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.commerce.PointsFideliteDTO;
import ma.mysuguclientapp.dtos.commerce.TransactionPointsDTO;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.FideliteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/fidelite")
@RequiredArgsConstructor
public class FideliteController {

    private final FideliteService fideliteService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<PointsFideliteDTO> getMesPoints(@AuthenticationPrincipal String email) {
        Long userId = getUserId(email);
        return ResponseEntity.ok(fideliteService.getPoints(userId));
    }

    @GetMapping("/historique")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<Page<TransactionPointsDTO>> getHistorique(
            @AuthenticationPrincipal String email,
            Pageable pageable) {
        Long userId = getUserId(email);
        return ResponseEntity.ok(fideliteService.getHistoriquePoints(userId, pageable));
    }

    @GetMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PointsFideliteDTO> getPointsAdmin(@PathVariable Long userId) {
        return ResponseEntity.ok(fideliteService.getPoints(userId));
    }

    private Long getUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }
}
